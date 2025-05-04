using UnityEngine;
using TMPro; // TextMeshPro ���ӽ����̽� �߰�

public class GameController : MonoBehaviour
{
    // Text ��� TMP_Text ���
    public TMP_Text debugText;

    // �̱��� ���� ����
    public static GameController Instance { get; private set; }

    void Awake()
    {
        // �̱��� ����
        if (Instance == null)
        {
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }
        else
        {
            Destroy(gameObject);
        }
    }

    void Start()
    {
        Debug.Log("GameController ���۵�");

        // TextMeshPro �ڵ� ã�� (���� ������ �� �� ���)
        if (debugText == null)
        {
            debugText = GameObject.Find("StatusText")?.GetComponent<TMP_Text>();
        }

        if (debugText != null)
        {
            debugText.text = "�ȵ���̵� ���� ��� ��...";
        }
    }

    // �ȵ���̵忡�� ȣ��� �޼ҵ�
    public void OnMessageReceived(string message)
    {
        Debug.Log("�ȵ���̵�κ��� �޽��� ����: " + message);

        // UI ������Ʈ
        if (debugText != null)
        {
            debugText.text = "�ȵ���̵� �޽���: " + message;
        }

        // ������ �޽��� ó�� - ���� ���� ����
        if (message.StartsWith("color:"))
        {
            string colorName = message.Substring(6);
            ChangeBackgroundColor(colorName);
        }
    }

    private void ChangeBackgroundColor(string colorName)
    {
        Color newColor = Color.white; // �⺻��

        switch (colorName.ToLower())
        {
            case "red": newColor = Color.red; break;
            case "green": newColor = Color.green; break;
            case "blue": newColor = Color.blue; break;
        }

        Camera.main.backgroundColor = newColor;
    }

    // �ȵ���̵�� �޽��� ����
    public void SendMessageToAndroid(string message)
    {
        Debug.Log("�ȵ���̵�� �޽��� ����: " + message);

#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                using (AndroidJavaObject context = activity.Call<AndroidJavaObject>("getApplicationContext"))
                {
                    // ��ε�ĳ��Ʈ ����Ʈ ����
                    AndroidJavaObject intent = new AndroidJavaObject("android.content.Intent");
                    intent.Call<AndroidJavaObject>("setAction", "com.yourdomain.UNITY_MESSAGE");
                    intent.Call<AndroidJavaObject>("putExtra", "message", message);
                    context.Call("sendBroadcast", intent);
                }
            }
        }
#else
        Debug.Log("�ȵ���̵� �÷��������� �۵��մϴ�.");
#endif
    }

    // UI ��ư�� ������ �޼ҵ�
    public void OnSendButtonClicked()
    {
        SendMessageToAndroid("Unity ��ư�� Ŭ����!");
    }
}