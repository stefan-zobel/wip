using Microsoft.Extensions.Logging;
using System.IO.Pipelines;
using System.Net.Sockets;

namespace TelegramServer;

public sealed class ServerEndpoint2
{
    private static readonly ILogger<ServerEndpoint2> LOGGER = LogManager.GetLogger<ServerEndpoint2>();

    private const int DEFAULT_ACCEPT_TIMEOUT_MILLIS = 500;
    private static readonly string NO_PROCESSOR_REGISTERED_MSG = $"No {typeof(IMessageAndReplyHandler).FullName} processor registered";

    private readonly int _port;
    private readonly string _id;
    private readonly int _acceptTimeoutMillis;
    private readonly byte[] _frameDelimiter;

    private volatile bool _isListening = true;
    private Thread? _asyncStarter = null;
    private TcpListener? _srvSocket;
    private IMessageAndReplyHandler? _frameProcessor;

    public ServerEndpoint2(string id, int port)
        : this(id, port, DEFAULT_ACCEPT_TIMEOUT_MILLIS, Protocol.END_OF_REQUEST) { }

    public ServerEndpoint2(string id, int port, byte[] delimiter)
        : this(id, port, DEFAULT_ACCEPT_TIMEOUT_MILLIS, delimiter) { }

    public ServerEndpoint2(string id, int port, int acceptTimeoutMillis, byte[] delimiter)
    {
        if (delimiter == null || delimiter.Length == 0)
            throw new ArgumentException("delimiter null or empty");

        _port = port;
        _id = id;
        _acceptTimeoutMillis = acceptTimeoutMillis;
        _frameDelimiter = (byte[])delimiter.Clone();
    }

    public string Id => _id;

    public void RegisterMessageHandler(IMessageAndReplyHandler frameProcessor)
    {
        ArgumentNullException.ThrowIfNull(frameProcessor);
        _frameProcessor = frameProcessor;
    }

    public void StartAsync()
    {
        if (_frameProcessor == null) throw new InvalidOperationException(NO_PROCESSOR_REGISTERED_MSG);

        _asyncStarter = new Thread(() =>
        {
            try
            {
                Start();
            }
            catch (Exception e)
            {
                if (e is not WireException) throw new WireException(e);
                throw;
            }
        })
        {
            Name = $"{nameof(ServerEndpoint)}-{_port}-MasterThread-{GetHashCode()}",
            IsBackground = true
        };
        _asyncStarter.Start();
    }

    internal void Start()
    {
        // Use the static Create method for Dual Mode (IPv4 + IPv6) support
        _srvSocket = TcpListener.Create(_port);
        _srvSocket.ExclusiveAddressUse = false; // Equivalent to setReuseAddress
        _srvSocket.Start();

//        Interlocked.Exchange(ref _isRunning, 1);
        LOGGER.LogInformation("Server started on port {Port} (Dual Mode)", _port);

        // Note: .NET TcpListener.Accept doesn't use "SoTimeout" the same way.
        // We use Pending() check and Thread.Sleep to mimic the polling accept loop.

        while (_isListening)
        {
            TcpClient? client = AcceptNextConnection();
            if (client != null)
            {
                // Offload to ThreadPool (Equivalent to workerService.execute)
                Task.Run(() => HandleClient(client));
            }
        }
    }

    private async void HandleClient(TcpClient client)
    {
        using (client)
        using (NetworkStream stream = client.GetStream())
        {
            try
            {
                var pipe = PipeReader.Create(client.GetStream());
                byte[]? frame;
                while ((frame = await PipelinesFrameDecoder.NextFrameAsync(pipe, _frameDelimiter)) != null)
                {
                    using var ms = new MemoryStream();
                    _frameProcessor!.OnNext(frame, ms);

                    ms.Write(_frameDelimiter, 0, _frameDelimiter.Length);

                    byte[] response = ms.ToArray();
                    stream.Write(response, 0, response.Length);
                    stream.Flush();
                }
            }
            catch (Exception e)
            {
                if (e is IOException && e.InnerException is SocketException) return;
                throw new WireException(e);
            }
        }
    }

    private TcpClient? AcceptNextConnection()
    {
        while (_isListening)
        {
            try
            {
                // Check if a connection is pending to avoid blocking indefinitely
                if (_srvSocket != null && _srvSocket.Pending())
                {
                    return _srvSocket.AcceptTcpClient();
                }
                Thread.Sleep(10); // Small sleep to prevent 100% CPU spike
            }
            catch (SocketException) { /* Ignore */ }
        }
        return null;
    }

    public void Stop()
    {
        _isListening = false;
        _srvSocket?.Stop();

        if (_asyncStarter != null)
        {
            try
            {
                _asyncStarter.Join(_acceptTimeoutMillis);
            }
            catch { /* Ignored */ }
            _asyncStarter = null;
        }
    }
}
