namespace Misc;

// Use this class with "using" to ensure proper disposal!
internal class TcpClientHandler : IDisposable
{
    // timeout between to telegrams (KeepAlive Time = 200 s + 5 s on top)
    private static readonly TimeSpan IdleTimeout = TimeSpan.FromSeconds(205); // todo
    // timeout for a single read operation
    private static readonly TimeSpan SingleReadTimeout = TimeSpan.FromSeconds(15); // todo

    private CancellationTokenSource _idleTimeoutCts;

    private readonly TcpClient _client;
    private readonly NetworkStream _stream;
    private readonly CancellationToken _ct;
    private volatile bool _disposed = false;

    internal TcpClientHandler(TcpClient client, CancellationToken ct)
    {
        _client = client ?? throw new ArgumentNullException(nameof(client));

        // Source for the 205 s idle timeout
        _idleTimeoutCts = new CancellationTokenSource();
        _ct = ct;

        // Nagle's algorithm (false=ON)/(true=OFF)
        _client.NoDelay = true; // false; // todo
        _client.ReceiveBufferSize = 64 * 1_024;

        _stream = _client.GetStream();
    }

    /// <summary>
    /// Starts the asynchronous handling of the TCP connection.
    /// This is the main "work" method of the handler.
    /// </summary>
    public async Task RunAsync()
    {
        try
        {
            int expectedLength = 1234; // TODO: determine the correct length to read
            while (!_ct.IsCancellationRequested && _client.Connected)
            {
                // Start the timer before the read
                _idleTimeoutCts.CancelAfter(IdleTimeout);
                // Link external cancellation with our idle timeout
                // This token gets canceled when either _ct is canceled OR _idleTimeoutCts is canceled.
                using var readCts = CancellationTokenSource.CreateLinkedTokenSource(_ct, _idleTimeoutCts.Token);

                // Await data using the linked token
                ReadResult result = await _stream.ReadExactAsync(expectedLength, SingleReadTimeout, readCts.Token).ConfigureAwait(false);

                switch (result.Status)
                {
                    case ReadStatus.Eof:
                        Console.WriteLine("Connection closed by remote peer (EOF).");
                        _idleTimeoutCts.Dispose();
                        // immediately exit
                        return;
                    case ReadStatus.Empty:
                        // no data received at all
                        continue;
                    case ReadStatus.Timeout:
                        Console.WriteLine("A single read operation timed out.");
                        // this is an incomplete telegram
                        _idleTimeoutCts.Dispose();
                        // immediately exit
                        return;
                    case ReadStatus.Success:
                        // We successfully received data, stop the idle timer (processing time doesn't count).
                        _idleTimeoutCts.Cancel();

                        // Process the received data
                        byte[]? data = result.Data;
                        if (data != null && data.Length > 0) {
                            Console.WriteLine($"Received {data.Length} bytes of data.");
                            await ProcessRequest(data, _ct).ConfigureAwait(false);
                        }
                        // --- Processing finished ---
                        // Loop continues automatically
                        break;
                } // switch

                // the old _idleTimeoutCts is permanently canceled, so
                // replace it with a new one before the loop proceeds
                _idleTimeoutCts.Dispose();
                _idleTimeoutCts = new CancellationTokenSource();

                // loop starts again and uses the new _idleTimeoutCts
            } // while

            // clean-up on regular loop exit
            _idleTimeoutCts.Dispose();
        }
        catch (OperationCanceledException) when (_idleTimeoutCts.Token.IsCancellationRequested)
        {
            // THIS CATCH BLOCK IS NOW THE ONLY WAY THE 205s IDLE TIMEOUT TRIGGERS TERMINATION.
            Console.WriteLine($"Connection idle timeout reached: ({IdleTimeout.TotalSeconds}s).");
        }
        /*
        catch (OperationCanceledException) when (_combinedCt.IsCancellationRequested)
        {
            Console.WriteLine("Run operation explicitly cancelled either by external token or via idle timeout.");
        }
        */
        catch (OperationCanceledException)
        {
            Console.WriteLine("External cancellation occurred.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"An error occurred during RunAsync: {ex.Message}");
        }
        finally
        {
            // Ensure cleanup happens if the loop exits naturally or via exception
            Dispose();
        }
    }
    private async Task ProcessRequest(byte[] data, CancellationToken ct)
    {
        // The global idle timer is paused during this entire operation.
        await Task.Delay(1000, CancellationToken.None); // Simulate some async work/processing time
        Console.WriteLine("Finished processing request.");
    }

    ~TcpClientHandler()
    {
        Dispose(false);
    }

    public void Dispose()
    {
        Dispose(true);
        GC.SuppressFinalize(this);
    }

    protected virtual void Dispose(bool disposing)
    {
        if (!Interlocked.Exchange(ref _disposed, true))
        {
            if (disposing)
            {
                _idleTimeoutCts?.Dispose();
                _stream?.Dispose();
                _client?.Dispose();
            }
        }
    }
}
