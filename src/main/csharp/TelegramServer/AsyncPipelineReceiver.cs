using System.IO.Pipelines;
using System.Net.Sockets;

namespace TelegramServer;

public class AsyncPipelineReceiver
{
    private readonly TcpClient _client;
    private readonly byte[] _delimiter;
    private readonly IMessageHandler _handler;
    private CancellationTokenSource? _cts;

    public AsyncPipelineReceiver(TcpClient client, byte[] delimiter, IMessageHandler handler)
    {
        _client = client;
        _delimiter = delimiter;
        _handler = handler;
    }

    public void Start()
    {
        _cts = new CancellationTokenSource();
        // Process in a background task (Fire-and-Forget)
        _ = ProcessLinesAsync(_client, _cts.Token);
    }

    public void Stop() => _cts?.Cancel();

    private async Task ProcessLinesAsync(TcpClient client, CancellationToken ct)
    {
        using var stream = client.GetStream();
        var reader = PipeReader.Create(stream);

        try
        {
            while (!ct.IsCancellationRequested)
            {
                byte[]? frame = await PipelinesFrameDecoder.NextFrameAsync(reader, _delimiter, ct);

                if (frame == null) break; // Connection closed

                // Process the Frame (Handler must be fast or OnNext() should be asynchronous)
                _handler.OnNext(frame);
            }
        }
        catch (OperationCanceledException) { /* Normal Shutdown */ }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Pipeline Error: {ex.Message}");
        }
        finally
        {
            await reader.CompleteAsync();
            client.Close();
        }
    }
}
