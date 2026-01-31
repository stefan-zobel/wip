namespace Misc;

public class TcpListenerHostedService : BackgroundService
{
    private readonly ILogger<TcpListenerHostedService> _logger;

    public TcpListenerHostedService(ILogger<TcpListenerHostedService> logger)
    {
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            if (_logger.IsEnabled(LogLevel.Information))
            {
                _logger.LogInformation("TcpListenerHostedService running at: {time}", DateTimeOffset.Now);

                var server = new TcpServer(IPAddress.Any, 24024, StaticLoggerFactory.GetLogger<TcpServer>(), null /*TODO*/, true);
                server.Start();

                Console.WriteLine("Press ENTER to stop...");
                Console.ReadLine();

                await server.StopAsync();
                server.Dispose();
            }
            await Task.Delay(1000, stoppingToken);
        }
    }
}
