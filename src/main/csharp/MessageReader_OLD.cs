using System.Diagnostics;

namespace Misc;

/// <summary>
/// Provides buffered, header-aware reading of messages from a network stream.
/// TODO : switch this to use a PipeReader.
/// </summary>
/// <remarks>It maintains an internal buffer to handle partial reads and to synchronize
/// on message boundaries (skipping initial "dirty bytes"), ensuring that headers and
/// payloads are read in full even when network data arrives in fragments.
/// </remarks>
internal class MessageReader_OLD : IDisposable
{
    private const int MaxRecursionDepth = 10;
    private const int MaxDirtyBytesCount = 8 * 1_024;
    private const int HeaderSize = 17;
    private const int TempBufferSize = 16 * 1_024;
    private const long MessageReadTimeoutSeconds = 15L;
    private const byte StartByte = (byte) 'P';

    // A shared instance of the ArrayPool for renting and returning buffers.
    private static readonly ArrayPool<byte> Pool = ArrayPool<byte>.Shared;
    private static readonly TimeSpan _totalTimeout = TimeSpan.FromSeconds(MessageReadTimeoutSeconds);
    // Static logger instance because of high-frequency instantiation
    private static readonly ILogger<MessageReader_OLD> _logger = StaticLoggerFactory.GetLogger<MessageReader_OLD>();

    private volatile bool _disposed = false;
    private readonly CancellationToken _ct;

    // A buffer to hold data read from the network that hasn't been processed yet.
    private readonly MemoryStream _buffer = new MemoryStream(2 * 1_024);
    private readonly NetworkStream _networkStream;

    private long _startTime;

    internal MessageReader_OLD(NetworkStream stream, CancellationToken ct)
    {
        _ct = ct;
        _networkStream = stream ?? throw new ArgumentNullException(nameof(stream));
    }

    /// <summary>
    /// Reads from the network stream and buffers the data.
    /// </summary>
    private async Task FillBufferAsync()
    {
        // Rent a buffer from the shared ArrayPool (16 KiB)
        byte[] rentedBuffer = Pool.Rent(TempBufferSize);
        try
        {
            // Use the rented buffer to read from the network
            int bytesRead = await _networkStream.ReadAsync(rentedBuffer, _ct).ConfigureAwait(false);

            if (bytesRead == 0)
            {
                throw new EndOfStreamException("Connection closed.");
            }

            // Append newly read data to the internal MemoryStream
            _buffer.Write(rentedBuffer, 0, bytesRead);
        }
        finally
        {
            // return the buffer to the pool
            Pool.Return(rentedBuffer);
        }
    }

    /// <summary>
    /// Synchronizes the stream to the next 'P' byte, extracts the 17-byte header, 
    /// and ensures the rest of the stream remains available in the buffer.
    /// </summary>
    internal async Task<byte[]> GetHeaderAsync(ConnectionId conId)
    {
        return await GetHeaderAsync(conId, 0).ConfigureAwait(false);
    }

    private async Task<byte[]> GetHeaderAsync(ConnectionId conId, int recursionDepth)
    {
        // Ensure we have enough data to check the first 17 bytes
        while (_buffer.Length < HeaderSize)
        {
            await FillBufferAsync().ConfigureAwait(false);
        }

        // We now have enough data in our buffer to check for the 'P' start of the header.
        byte[] inspectionBytes = _buffer.ToArray();

        int pIndex = -1;
        // Iterate only over locations where a full header could start.
        // 'i <= bufferedBytes.Length - HeaderSize' ensures that any 'pIndex' found has
        // at least 16 bytes following it in the current array
        for (int i = 0; i <= inspectionBytes.Length - HeaderSize; i++)
        {
            if (inspectionBytes[i] == StartByte)
            {
                pIndex = i;
                break;
            }
        }

        if (pIndex == -1)
        {
            if (recursionDepth > MaxRecursionDepth - 1 || _buffer.Length >= MaxDirtyBytesCount)
            {
                // Prevent infinite memory growth or unbounded recursion from bad data
                throw new InvalidDataException($"{conId} : Could not find header start byte 'P' within a reasonable"
                    + " buffer size ({_buffer.Length}) or number of recursions ({recursionDepth}).");
            }
            // 'P' wasn't found in the current 'inspectionBytes' buffer. 
            // Discard the initial junk data up to the last 16 bytes to
            // check again in the next recursive call.
            KeepLast16Bytes(conId, _buffer);

            // Recurse until we find it
            return await GetHeaderAsync(conId, recursionDepth + 1).ConfigureAwait(false);
        }
        else if (pIndex > 0)
        {
            // We have found 'P' and pIndex > 0 implies that there are some dirty bytes.
            _logger.LogWarning("{Con} : {DirtyByteCount} dirty bytes (at the beginning of a header) received: '{DirtyBytes}'",
                conId, pIndex, Util.ByteArrayToString(inspectionBytes, 0, pIndex));
        }

        // --- Found 'P' at pIndex ---

        // Extract the 17-byte header
        byte[] header = new byte[HeaderSize];
        // Position the stream cursor correctly at the start of the header
        _buffer.Position = pIndex;
        _buffer.Read(header, 0, HeaderSize);

        // Shift the remaining data in the buffer. We use a helper method
        // to trim the stream from the start.
        ShiftBufferStart(_buffer, pIndex + HeaderSize);

        return header;
    }

    private static void KeepLast16Bytes(ConnectionId conId, MemoryStream stream)
    {
        // keepLength = 16 bytes
        const int keepLength = HeaderSize - 1;
        long currentLength = stream.Length;

        if (currentLength <= keepLength)
        {
            stream.Position = currentLength;
            return;
        }

        int bytesToRemove = (int) currentLength - keepLength;

        // Read the dirty bytes into an array for the logging call
        byte[] dirtyBytesForLog = new byte[bytesToRemove];
        stream.Position = 0;
        stream.Read(dirtyBytesForLog, 0, bytesToRemove);

        // Log the message
        _logger.LogWarning("{Con} : {DirtyByteCount} dirty bytes (at the beginning of a telegram) received: '{DirtyBytes}'",
            conId, bytesToRemove, Util.ByteArrayToString(dirtyBytesForLog, 0, bytesToRemove));

        // --- Now shift the remaining data in place ---

        // Get the underlying buffer (no allocations)
        byte[] internalBuffer = stream.GetBuffer();

        // Shift the last 16 bytes to the start of the buffer
        // Source index is bytesToRemove, destination index is 0
        Buffer.BlockCopy(internalBuffer, bytesToRemove, internalBuffer, 0, keepLength);

        // Trim the stream length to exactly 16 bytes
        stream.SetLength(keepLength);

        // Position the stream cursor at the end, ready for further appending
        stream.Position = keepLength;
    }

    // Helper method to efficiently remove data from the start of a MemoryStream
    private static void ShiftBufferStart(MemoryStream stream, int bytesToRemove)
    {
        // Get the underlying buffer array and current length
        byte[] buffer = stream.GetBuffer();
        int remainingLength = (int) stream.Length - bytesToRemove;

        // Shift the remaining data to the beginning of the internal array
        Buffer.BlockCopy(buffer, bytesToRemove, buffer, 0, remainingLength);

        // Update the stream length and position pointers
        stream.SetLength(remainingLength);
        // We must move the position to the *end* of the valid data we just
        // shifted because we want to be ready for further appending.
        stream.Position = remainingLength;
    }

    /// <summary>
    /// Read subsequent payload data using the existing buffer.
    /// </summary>
    internal async Task<byte[]> ReadPayloadAsync(ConnectionId conId, int length)
    {
        // Ensure enough data is available in the buffer
        // TODO : this represents a real problem when the length in the header is too large!
        // (We will never stop try reading what isn't there)
        while (_buffer.Length < length)
        {
            await FillBufferAsync().ConfigureAwait(false);
        }

        // Extract the payload data
        byte[] payload = new byte[length];

        // Position the stream cursor at the beginning
        // (it should be 0 from the last operation)
        _buffer.Position = 0;

        // Read the exact amount of payload data
        _buffer.Read(payload, 0, length);

        // Remove the processed payload bytes from the buffer (in-place).
        // The ShiftBufferStart method handles resetting the length and position.
        ShiftBufferStart(_buffer, length);

        // The buffer is now ready to read the next header (append more data).
        return payload;
    }

    ~MessageReader_OLD()
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
                try { _networkStream?.Dispose(); } catch { }
                try { _buffer?.Dispose(); } catch { }
            }
        }
    }

    /// <summary>
    /// An alternative implementation. Not much better.
    /// It seems .NET aborts the underlying socket operation at the OS level when
    /// you use a CancellationTokenSource together with NetworkStream.ReadAsync.
    /// That means the socket becomes unusable once the token triggers, we neither
    /// can send back an error reply nor can we resume listening for the next message
    /// from that socket. That stinks.
    /// </summary>
    /// <param name="timeoutToken"></param>
    /// <returns></returns>
    /// <exception cref="EndOfStreamException"></exception>
    private async Task FillBufferAsync2(CancellationToken timeoutToken = default)
    {
        // Link the global shutdown (_ct) with the caller's timeout token
        CancellationTokenSource? linkedCts = null;
        if (timeoutToken != default && timeoutToken != _ct)
        {
            linkedCts = CancellationTokenSource.CreateLinkedTokenSource(_ct, timeoutToken);
        }
        // Either use the linked token or fall back to the global shutdown token
        CancellationToken effectiveToken = linkedCts?.Token ?? _ct;

        // Rent a buffer from the shared ArrayPool (16 KiB)
        byte[] rentedBuffer = Pool.Rent(TempBufferSize);
        try
        {
            // Use the rented buffer to read from the network
            int bytesRead = await _networkStream.ReadAsync(rentedBuffer, effectiveToken).ConfigureAwait(false);

            if (bytesRead == 0)
            {   // EOF, we'll receive WSAECONNABORTED (10053) on the next read attempt
                return;
            }

            // Append newly read data to the internal MemoryStream
            _buffer.Write(rentedBuffer, 0, bytesRead);
        }
        catch (IOException ex) when (ex.InnerException is SocketException { NativeErrorCode: 10053 })
        {
            throw new EndOfStreamException("Connection closed.");
        }
        finally
        {
            // return the buffer to the pool
            Pool.Return(rentedBuffer);

            // Unlink from the parent tokens to prevent memory leaks
            linkedCts?.Dispose();
        }
    }

    /// <summary>
    /// An alternative implementation. Not much better.
    /// </summary>
    /// <param name="conId"></param>
    /// <param name="recursionDepth"></param>
    /// <returns></returns>
    /// <exception cref="TimeoutException"></exception>
    /// <exception cref="InvalidDataException"></exception>
    private async Task<byte[]> GetHeaderAsync2(ConnectionId conId, int recursionDepth)
    {
        // Wait indefinitely for the first byte of a new message.
        // This uses the global shutdown token (_ct) but no timeout.
        while (_buffer.Length == 0)
        {
            await FillBufferAsync2().ConfigureAwait(false);
        }

        // Message has started, start the high-resolution stopwatch.
        _startTime = Stopwatch.GetTimestamp();

        // Ensure we have enough data to check the first 17 bytes
        while (_buffer.Length < HeaderSize)
        {
            TimeSpan remaining = _totalTimeout - Stopwatch.GetElapsedTime(_startTime);
            if (remaining <= TimeSpan.Zero)
            {
                throw new TimeoutException("Header read exceeded timeout.");
            }

            // Create, use, and immediately dispose the timeout source
            // for this specific read
            using var timeoutCts = new CancellationTokenSource(remaining);

            await FillBufferAsync2(timeoutCts.Token).ConfigureAwait(false);
        }

        // We now have enough data in our buffer to check for the 'P' start of the header.
        byte[] inspectionBytes = _buffer.ToArray();

        int pIndex = -1;
        // Iterate only over locations where a full header could start.
        // 'i <= bufferedBytes.Length - HeaderSize' ensures that any 'pIndex' found has
        // at least 16 bytes following it in the current array
        for (int i = 0; i <= inspectionBytes.Length - HeaderSize; i++)
        {
            if (inspectionBytes[i] == StartByte)
            {
                pIndex = i;
                break;
            }
        }

        if (pIndex == -1)
        {
            if (recursionDepth > MaxRecursionDepth - 1 || _buffer.Length >= MaxDirtyBytesCount)
            {
                // Prevent infinite memory growth or unbounded recursion from bad data
                throw new InvalidDataException($"{conId} : Could not find header start byte 'P' within a reasonable"
                    + " buffer size ({_buffer.Length}) or number of recursions ({recursionDepth}).");
            }
            // 'P' wasn't found in the current 'inspectionBytes' buffer. 
            // Discard the initial junk data up to the last 16 bytes to
            // check again in the next recursive call.
            KeepLast16Bytes(conId, _buffer);

            // Recurse until we find it
            return await GetHeaderAsync(conId, recursionDepth + 1).ConfigureAwait(false);
        }
        else if (pIndex > 0)
        {
            // We have found 'P' and pIndex > 0 implies that there are some dirty bytes.
            _logger.LogWarning("{Con} : {DirtyByteCount} dirty bytes (at the beginning of a header) received: '{DirtyBytes}'",
                conId, pIndex, Util.ByteArrayToString(inspectionBytes, 0, pIndex));
        }

        // --- Found 'P' at pIndex ---

        // Extract the 17-byte header
        byte[] header = new byte[HeaderSize];
        // Position the stream cursor correctly at the start of the header
        _buffer.Position = pIndex;
        _buffer.Read(header, 0, HeaderSize);

        // Shift the remaining data in the buffer. We use a helper method
        // to trim the stream from the start.
        ShiftBufferStart(_buffer, pIndex + HeaderSize);

        return header;
    }

    /// <summary>
    /// An alternative implementation. Not much better.
    /// </summary>
    /// <param name="conId"></param>
    /// <param name="length"></param>
    /// <returns></returns>
    internal async Task<byte[]> ReadPayloadAsync2(ConnectionId conId, int length)
    {
        // Calculate final remaining time for the payload
        TimeSpan payloadRemainingTime = _totalTimeout - Stopwatch.GetElapsedTime(_startTime);
        // Ensure we don't pass a negative/zero duration to the CTS
        if (payloadRemainingTime <= TimeSpan.Zero)
        {
            payloadRemainingTime = TimeSpan.FromMilliseconds(1);
        }

        // Setup timeout TokenSource
        using var timeoutCts = new CancellationTokenSource(payloadRemainingTime);

        try
        {
            // 'lastLength' is just used as a stopping condition
            // (_buffer.Length != lastLength) will be true on the
            // first entry into the loop and become false when the
            // buffer doesn't accumulate more bytes)
            int lastLength = -1;
            // Ensure enough data is available in the buffer
            while (_buffer.Length < length && _buffer.Length != lastLength)
            {
                lastLength = (int)_buffer.Length;
                await FillBufferAsync2(timeoutCts.Token).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException)
        {
            // Re-throw if a server shutdown was requested
            if (_ct.IsCancellationRequested) throw;
        }

        // Extract what we actually have (up to 'length')
        int actualToRead = Math.Min((int)_buffer.Length, length);
        byte[] payload = new byte[actualToRead];

        _buffer.Position = 0;
        _buffer.Read(payload, 0, actualToRead);

        // Shift the buffer by the amount we actually read
        ShiftBufferStart(_buffer, actualToRead);

        // If the client lied, we'll be dead in a moment anyway
        // (we won't even be able to send an error response).
        if (actualToRead < length)
        {
            _logger.LogWarning("{Con} : Framing mismatch. Expected {length} bytes, but got only {actualToRead} bytes."
                + " Connection will be closed.", conId, length, actualToRead);
        }

        return payload;
    }
}
