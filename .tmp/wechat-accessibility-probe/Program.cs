using System.Diagnostics;
using System.Runtime.InteropServices;
using FlaUI.Core.Definitions;
using FlaUI.UIA3;

const uint SpiGetScreenReader = 0x0046;
const uint SpiSetScreenReader = 0x0047;
const uint SendChange = 0x0002;

var original = false;
if (!SystemParametersInfo(SpiGetScreenReader, 0, ref original, 0))
{
    throw new InvalidOperationException("无法读取 Windows 屏幕阅读器标志。");
}

Console.WriteLine($"ORIGINAL_SCREEN_READER={original}");
try
{
    var enabled = true;
    if (!SystemParametersInfo(SpiSetScreenReader, 1, ref enabled, SendChange))
    {
        throw new InvalidOperationException("无法临时启用 Windows 屏幕阅读器标志。");
    }

    await Task.Delay(TimeSpan.FromSeconds(3));
    using var automation = new UIA3Automation();
    var process = Process.GetProcessesByName("Weixin")
        .Single(item => item.MainWindowHandle != IntPtr.Zero);
    var window = automation.GetDesktop().FindFirstChild(cf =>
        cf.ByProcessId(process.Id).And(cf.ByControlType(ControlType.Window)));
    var count = window?.FindAllDescendants().Length ?? 0;
    Console.WriteLine($"DESCENDANTS_WITH_SCREEN_READER={count}");
}
finally
{
    var restore = original;
    var restored = SystemParametersInfo(SpiSetScreenReader, original ? 1u : 0u, ref restore, SendChange);
    Console.WriteLine($"RESTORED={restored} VALUE={original}");
}

[DllImport("user32.dll", SetLastError = true)]
static extern bool SystemParametersInfo(uint uiAction, uint uiParam, ref bool pvParam, uint fWinIni);
