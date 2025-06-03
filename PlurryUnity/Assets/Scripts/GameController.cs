using UnityEngine;

public class GameController : MonoBehaviour
{
    private string displayText = "TEST";
    public GUISkin skin;

    void Start()
    {
        
    }

    // �ȵ���̵忡�� ȣ��� �޼ҵ�
    public void OnMessageReceived(string message)
    {

    }

    // �ȵ���̵�� �޽��� ����
    public void SendMessageToAndroid()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        CallAndroidFunction();
#else
        Debug.Log("�ȵ���̵� �÷��������� �۵��մϴ�.");
#endif
    }

    private void CallAndroidFunction()
    {
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                // RaisingMainActivity�� showFloatingPopup() �޼��� ���� ȣ��
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