using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using FlaUI.Core.Capturing;
using RapidOCRLib;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Definitions;
using FlaUI.UIA3;

const string targetGroup = "报数自动入库测试";

var processes = Process.GetProcessesByName("Weixin")
    .Where(item => item.MainWindowHandle != IntPtr.Zero)
    .ToList();
if (processes.Count != 1)
{
    throw new InvalidOperationException($"要求唯一可见微信主窗口，当前为 {processes.Count} 个。");
}

using var automation = new UIA3Automation();
var process = processes[0];
var windowElements = automation.GetDesktop().FindAllChildren(cf =>
    cf.ByProcessId(process.Id));
if (windowElements.Length == 0)
{
    throw new InvalidOperationException("未找到微信顶层 UIAutomation 元素。");
}

var mainWindowElement = windowElements.SingleOrDefault(element =>
    element.ControlType == ControlType.Window &&
    string.Equals(Safe(() => element.Properties.ClassName.ValueOrDefault), "Qt51514QWindowIcon", StringComparison.Ordinal));
if (mainWindowElement == null)
{
    throw new InvalidOperationException("未找到唯一的微信 Qt 主窗口。");
}

Console.WriteLine($"TOP_LEVEL_UIA={windowElements.Length}");
var shouldSelectTarget = args.Contains("--select-visible-target", StringComparer.Ordinal);
if (shouldSelectTarget)
{
    var bounds = mainWindowElement.BoundingRectangle;
    // 仅用于本轮隔离验收：目标群在最新截图中是左侧第二个可见会话。
    var x = bounds.Left + 205;
    var y = bounds.Top + 212;
    SetCursorPos(x, y);
    mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
    mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    await Task.Delay(1500);
    Console.WriteLine("VISIBLE_TARGET_SELECTED");
}
var elements = windowElements.SelectMany((element, index) =>
{
    var descendants = element.FindAllDescendants();
    Console.WriteLine($"UIA[{index}] type={element.ControlType} class={Safe(() => element.Properties.ClassName.ValueOrDefault)} descendants={descendants.Length}");
    return descendants;
}).ToArray();

var structural = elements
    .Select(element => new
    {
        Type = element.ControlType.ToString(),
        Class = Safe(() => element.Properties.ClassName.ValueOrDefault),
        Id = Safe(() => element.Properties.AutomationId.ValueOrDefault),
        IsTarget = string.Equals(Safe(() => element.Properties.Name.ValueOrDefault), targetGroup, StringComparison.Ordinal),
        IsSelected = SafeBool(() => element.Patterns.SelectionItem.PatternOrDefault?.IsSelected.ValueOrDefault)
    })
    .Where(item => true)
    .GroupBy(item => new { item.Type, item.Class, item.Id, item.IsTarget, item.IsSelected })
    .Select(group => new
    {
        group.Key.Type,
        group.Key.Class,
        group.Key.Id,
        group.Key.IsTarget,
        group.Key.IsSelected,
        Count = group.Count()
    })
    .OrderBy(item => item.Type)
    .ThenBy(item => item.Id)
    .ToList();

foreach (var item in structural)
{
    Console.WriteLine($"type={item.Type} class={item.Class} id={item.Id} targetGroup={item.IsTarget} selected={item.IsSelected} count={item.Count}");
}

var win32Classes = new List<(nint Hwnd, string ClassName)>();
EnumWindows((hwnd, _) =>
{
    GetWindowThreadProcessId(hwnd, out var pid);
    if (pid == process.Id)
    {
        win32Classes.Add((hwnd, GetClassNameValue(hwnd)));
        EnumChildWindows(hwnd, (child, _) =>
        {
            win32Classes.Add((child, GetClassNameValue(child)));
            return true;
        }, IntPtr.Zero);
    }
    return true;
}, IntPtr.Zero);

foreach (var group in win32Classes.GroupBy(item => item.ClassName).OrderBy(item => item.Key))
{
    Console.WriteLine($"WIN32_CLASS={group.Key} count={group.Count()}");
}

var renderPane = mainWindowElement.FindFirstDescendant(cf => cf.ByClassName("MMUIRenderSubWindow"));
if (renderPane != null)
{
    mainWindowElement.AsWindow().Focus();
    await Task.Delay(1200);
    var bounds = mainWindowElement.BoundingRectangle;
    using (var screen = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format32bppArgb))
    using (var graphics = Graphics.FromImage(screen))
    {
        graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);
        var screenPath = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "wechat-window-screen.png"));
        screen.Save(screenPath, ImageFormat.Png);
        Console.WriteLine($"WINDOW_SCREEN_CAPTURE={screenPath} size={screen.Width}x{screen.Height}");
    }
    using var full = renderPane.Capture();
    var cropX = (int)Math.Round(full.Width * 0.34);
    using var chatOnly = full.Clone(
        new Rectangle(cropX, 0, full.Width - cropX, full.Height),
        PixelFormat.Format32bppArgb);
    var capturePath = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "wechat-chat-pane.png"));
    chatOnly.Save(capturePath, ImageFormat.Png);
    Console.WriteLine($"CHAT_PANE_CAPTURE={capturePath} size={chatOnly.Width}x{chatOnly.Height}");

    var modelDirectory = Path.Combine(AppContext.BaseDirectory, "models");
    var ocr = new OcrLite
    {
        DetPath = Path.Combine(modelDirectory, "ch_PP-OCRv5_mobile_det.onnx"),
        ClsPath = Path.Combine(modelDirectory, "ch_ppocr_mobile_v2.0_cls_infer.onnx"),
        RecPath = Path.Combine(modelDirectory, "ch_PP-OCRv5_rec_mobile_infer.onnx"),
        KeyDicPath = Path.Combine(modelDirectory, "ppocrv5_dict.txt"),
        ThreadNum = Math.Max(1, Environment.ProcessorCount / 2)
    };
    await ocr.InitModels();
    var ocrResult = ocr.Detect(chatOnly, 0, Math.Max(chatOnly.Width, chatOnly.Height), 0.4f, 0.3f, 1.6f, true, false);
    var markerBlocks = ocrResult.TextBlocks.Where(block =>
        !string.IsNullOrWhiteSpace(block.Text) &&
        block.Text.Contains("WX-UAT-", StringComparison.OrdinalIgnoreCase)).ToList();
    var allBlocks = ocrResult.TextBlocks.Where(block => !string.IsNullOrWhiteSpace(block.Text)).ToList();
    var capturedAt = DateTimeOffset.Now;
    var captureRecords = new List<object>();
    foreach (var block in markerBlocks)
    {
        var left = block.BoxPoints.Min(point => point.X);
        var top = block.BoxPoints.Min(point => point.Y);
        var right = block.BoxPoints.Max(point => point.X);
        var isSelf = right >= chatOnly.Width * 0.75;
        var sender = isSelf
            ? "CURRENT_ACCOUNT"
            : allBlocks
                .Where(candidate => !candidate.Text.Contains("WX-UAT-", StringComparison.OrdinalIgnoreCase))
                .Select(candidate => new
                {
                    Block = candidate,
                    Left = candidate.BoxPoints.Min(point => point.X),
                    Bottom = candidate.BoxPoints.Max(point => point.Y)
                })
                .Where(candidate => candidate.Bottom <= top && top - candidate.Bottom <= 35 && Math.Abs(candidate.Left - left) <= 40)
                .OrderBy(candidate => top - candidate.Bottom)
                .Select(candidate => candidate.Block.Text.Trim())
                .FirstOrDefault() ?? "OTHER_MEMBER";
        var fingerprintSource = $"{targetGroup}|{sender}|{block.Text.Trim()}";
        var fingerprint = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(fingerprintSource)));
        captureRecords.Add(new
        {
            group = targetGroup,
            sender,
            senderType = isSelf ? "SELF" : "GROUP_MEMBER",
            content = block.Text.Trim(),
            capturedAt = capturedAt.ToString("O"),
            fingerprint
        });
        Console.WriteLine($"OCR_MATCH sender={sender} side={(isSelf ? "SELF" : "OTHER")} x={left} y={top} text={block.Text}");
    }

    if (captureRecords.Count > 0)
    {
        var jsonPath = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "wechat-marker-capture.json"));
        File.WriteAllText(jsonPath, JsonSerializer.Serialize(captureRecords, new JsonSerializerOptions { WriteIndented = true }), new UTF8Encoding(false));
        Console.WriteLine($"STRUCTURED_CAPTURE={jsonPath} count={captureRecords.Count}");
    }
}

static string Safe(Func<string?> getter)
{
    try { return getter() ?? string.Empty; }
    catch { return string.Empty; }
}

static bool SafeBool(Func<bool?> getter)
{
    try { return getter() ?? false; }
    catch { return false; }
}

static string GetClassNameValue(nint hwnd)
{
    var buffer = new StringBuilder(256);
    _ = GetClassName(hwnd, buffer, buffer.Capacity);
    return buffer.ToString();
}

[DllImport("user32.dll")]
static extern bool EnumWindows(EnumWindowsProc callback, nint lParam);

[DllImport("user32.dll")]
static extern bool EnumChildWindows(nint parent, EnumWindowsProc callback, nint lParam);

[DllImport("user32.dll")]
static extern uint GetWindowThreadProcessId(nint hwnd, out int processId);

[DllImport("user32.dll", CharSet = CharSet.Unicode)]
static extern int GetClassName(nint hwnd, StringBuilder className, int maxCount);

[DllImport("user32.dll")]
static extern bool SetCursorPos(int x, int y);

[DllImport("user32.dll")]
static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);

delegate bool EnumWindowsProc(nint hwnd, nint lParam);
