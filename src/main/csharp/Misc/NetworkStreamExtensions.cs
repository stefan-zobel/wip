namespace Misc;

public static class NetworkStreamExtensions
{
    // 64 KiB maximum buffer size for a single read
    private const int MAX_BUF_SIZE = 64 * 1_024;

    // A shared instance of the ArrayPool for renting and returning buffers.
    private static readonly ArrayPool<byte> Pool = ArrayPool<byte>.Shared;

    /// <summary>
    /// Reads <c>payloadLength</c> bytes from a NetworkStream with a timeout using pooled buffers.
    /// </summary>
    /// <param name="stream">The network stream to read from.</param>
    /// <param name="payloadLength">The expected length of the payload.</param>
    /// <param name="timeout">The maximum time allowed for the read.</param>
    /// <param name="ct">A cancellation token.</param>
    /// <returns>A <c>ReadResult</c> containing all data read from the stream.</returns>
    public static async Task<ReadResult> ReadExactAsync(this NetworkStream stream, int payloadLength, TimeSpan timeout, CancellationToken ct)
    {
        if (payloadLength <= 0)
        {
            return ReadResult.Empty();
        }

        // Use a MemoryStream to accumulate the result chunks.
        using var resultBuffer = new MemoryStream(payloadLength);

        // Rent a buffer from the shared ArrayPool
        byte[] rentedBuffer = Pool.Rent(Math.Min(MAX_BUF_SIZE, payloadLength));

        // Create CancellationTokenSource for the timeout
        using var timeoutCts = new CancellationTokenSource(timeout);
        // The linkedCts will be canceled if either token gets canceled.
        using var linkedCts = CancellationTokenSource.CreateLinkedTokenSource(ct, timeoutCts.Token);
        CancellationToken linkedToken = linkedCts.Token;

        try
        {
            int numBytesMissing = payloadLength;
            while (numBytesMissing > 0)
            {
                int bytesRead = await stream.ReadAsync(rentedBuffer.AsMemory(0, rentedBuffer.Length), linkedToken).ConfigureAwait(false);
                if (bytesRead == 0)
                {
                    // EOF reached before reading expected payload length
                    return ReadResult.Eof();
                }
                // Write the data chunk to the result buffer
                resultBuffer.Write(rentedBuffer, 0, bytesRead);
                numBytesMissing -= bytesRead;
            }
        }
        catch (OperationCanceledException)
        {
            // The exception is caught here if either token was canceled.
            // Re-throw the original exception if it was the external token
            ct.ThrowIfCancellationRequested();
            // If we reach here, it was a timeout.
            // In that case return the incomplete data
            if (resultBuffer.Length > 0)
            {
                return ReadResult.Timeout(resultBuffer.ToArray());
            }
            else
            {
                return ReadResult.Empty();
            }
        }
        finally
        {
            // return the buffer to the pool
            Pool.Return(rentedBuffer);
        }

        // Return the accumulated payload
        return ReadResult.Success(resultBuffer.ToArray());
    }
}
