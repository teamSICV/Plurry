using UnityEngine;
using TMPro; // TextMeshPro ���ӽ����̽� �߰�

public class GameController : MonoBehaviour
{
 
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
}