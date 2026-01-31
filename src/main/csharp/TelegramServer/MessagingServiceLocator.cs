using System.Collections.Concurrent;

namespace TelegramServer;

/// <summary>
/// Manages Singleton instances for the messaging service.
/// </summary>
public static class MessagingServiceLocator
{
    private static readonly ConcurrentDictionary<Type, object> _services = new();

    /// <summary>
    /// Register a service instance.
    /// </summary>
    public static void RegisterService<T>(T implementation) where T : class
    {
        _services[typeof(T)] = implementation;
    }

    /// <summary>
    /// Retrieve a registered service instance.
    /// </summary>
    public static T GetService<T>() where T : class
    {
        if (_services.TryGetValue(typeof(T), out var service))
        {
            return (T)service;
        }

        throw new InvalidOperationException($"Service of Type {typeof(T).Name} couldn't be registered.");
    }

    /// <summary>
    /// Useful for Unit Tests.
    /// </summary>
    public static void Reset()
    {
        _services.Clear();
    }
}
