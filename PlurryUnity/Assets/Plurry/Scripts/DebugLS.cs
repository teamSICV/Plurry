using System.Diagnostics;
using UnityEngine;

public static class LogLS
{
    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    public static void Log(string message, Object context = null)
    {
        var frame = new StackFrame(1, true);
        var method = frame.GetMethod();
        var className = method.DeclaringType?.Name ?? "Unknown";
        var methodName = method.Name;

        string logMessage = $"[{className}] {methodName} : {message}";
        UnityEngine.Debug.Log(logMessage, context);

#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                activity.Call("UnityDebugLog", logMessage);
            }
        }
#endif
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    public static void Warning(string message, Object context = null)
    {
        var frame = new StackFrame(1, true);
        var method = frame.GetMethod();
        var className = method.DeclaringType?.Name ?? "Unknown";
        var methodName = method.Name;

        string logMessage = $"[{className}] {methodName} : {message}";
        UnityEngine.Debug.LogWarning(logMessage, context);

#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                activity.Call("UnityDebugWarning", logMessage);
            }
        }
#endif
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    public static void Error(string message, Object context = null)
    {
        var frame = new StackFrame(1, true);
        var method = frame.GetMethod();
        var className = method.DeclaringType?.Name ?? "Unknown";
        var methodName = method.Name;

        string logMessage = $"[{className}] {methodName} : {message}";
        UnityEngine.Debug.LogError(logMessage, context);

#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                activity.Call("UnityDebugError", logMessage);
            }
        }
#endif
    }
}
