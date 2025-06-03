using UnityEngine;

public class GameController : MonoBehaviour
{
    private string displayText = "TEST";
    public GUISkin skin;

    void Start()
    {
        
    }

    // 안드로이드에서 호출될 메소드
    public void OnMessageReceived(string message)
    {

    }

    // 안드로이드로 메시지 전송
    public void SendMessageToAndroid()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        CallAndroidFunction();
#else
        Debug.Log("안드로이드 플랫폼에서만 작동합니다.");
#endif
    }

    private void CallAndroidFunction()
    {
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                // RaisingMainActivity의 showFloatingPopup() 메서드 직접 호출
                activity.Call("showFloatingPopup");
            }
        }
    }

    private void PlayRaising()
    {
        displayText = "SUCCESS";
    }

    void OnGUI()
    {
        GUI.skin = skin;
        GUI.Label(new Rect(Screen.width / 2 - 50, Screen.height / 2 - 100, 100, 50), displayText, "Test");
    }
}