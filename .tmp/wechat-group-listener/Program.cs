using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.DependencyInjection;
using WeChatAuto.Components;
using WeChatAuto.Options;
using WeChatAuto.Services;

const string groupName = "报数自动入库测试";
const string testMarker = "WX-UAT-";

Console.OutputEncoding = Encoding.UTF8;
var outputPath = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "wechat-group-capture.jsonl"));
var seen = new HashSet<string>(StringComparer.Ordinal);
var sync = new object();

var provider = WeAutomation.Initialize(options =>
{
    options.DebugMode = false;
    options.InitAdressBook = false;
    options.EnableOCR = true;
    options.EnableMouseKeyboardSimulator = false;
    options.EnableRecordVideo = false;
});

var factory = provider.GetRequiredService<WeChatClientFactory>();
var clientNames = factory.GetWeChatClientNames();
if (clientNames.Count != 1)
{
    throw new InvalidOperationException($"为避免监听错账号，要求仅有一个微信实例；当前检测到 {clientNames.Count} 个。");
}

var client = factory.GetWeChatClient(clientNames[0]);
Console.WriteLine($"LISTENER_READY group={groupName} account={clientNames[0]}");
Console.WriteLine("仅保存包含 WX-UAT- 的新消息；不会回复消息。按 Ctrl+C 停止。");

using var shutdown = new CancellationTokenSource();
Console.CancelKeyPress += (_, eventArgs) =>
{
    eventArgs.Cancel = true;
    shutdown.Cancel();
};

await client.MessageMonitor.AddMessageListener(
    new[] { groupName },
    context =>
    {
        foreach (var message in context.NewMessages)
        {
            if (string.IsNullOrWhiteSpace(message.Message) ||
                !message.Message.Contains(testMarker, StringComparison.Ordinal))
            {
                continue;
            }

            var rawKey = $"{groupName}|{message.Who}|{message.Message}|{message.SendDate:yyyy-MM-dd HH:mm}|{message.MessageType}";
            var fingerprint = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(rawKey)));
            lock (sync)
            {
                if (!seen.Add(fingerprint))
                {
                    continue;
                }

                var record = new
                {
                    capturedAt = DateTimeOffset.Now,
                    group = groupName,
                    sender = message.Who,
                    content = message.Message,
                    messageTime = message.SendDate == default ? null : message.SendDate.ToString("yyyy-MM-dd HH:mm"),
                    messageType = message.MessageType.ToString(),
                    fingerprint
                };
                var json = JsonSerializer.Serialize(record);
                File.AppendAllText(outputPath, json + Environment.NewLine, new UTF8Encoding(false));
                Console.WriteLine($"CAPTURED sender={record.sender} time={record.messageTime ?? "unknown"} content={record.content}");
            }
        }
    },
    IsOpenMonitor: false,
    userToken: shutdown.Token,
    options: new MessageMonitorOptions
    {
        FetchFriendInfo = false,
        FetchImage = false,
        ClickRedEnvelope = false,
        IsRiskPrevention = false
    });

try
{
    await Task.Delay(Timeout.Infinite, shutdown.Token);
}
catch (OperationCanceledException)
{
    // Normal shutdown.
}
finally
{
    factory.Dispose();
    (provider as IDisposable)?.Dispose();
}
