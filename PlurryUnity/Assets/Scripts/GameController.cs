using UnityEngine;
using TMPro; // TextMeshPro 네임스페이스 추가

public class GameController : MonoBehaviour
{
    // Text 대신 TMP_Text 사용
    public TMP_Text debugText;

    // 싱글톤 패턴 구현
    public static GameController Instance { get; private set; }

    void Awake()
    {
        // 싱글톤 설정
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
        Debug.Log("GameController 시작됨");

        // TextMeshPro 자동 찾기 (수동 연결이 안 될 경우)
        if (debugText == null)
        {
            debugText = GameObject.Find("StatusText")?.GetComponent<TMP_Text>();
        }

        if (debugText != null)
        {
            debugText.text = "안드로이드 연결 대기 중...";
        }
    }

    // 안드로이드에서 호출될 메소드
    public void OnMessageReceived(string message)
    {
        Debug.Log("안드로이드로부터 메시지 수신: " + message);

        // UI 업데이트
        if (debugText != null)
        {
            debugText.text = "안드로이드 메시지: " + message;
        }

        // 간단한 메시지 처리 - 색상 변경 예시
        if (message.StartsWith("color:"))
        {
            string colorName = message.Substring(6);
            ChangeBackgroundColor(colorName);
        }
    }

    private void ChangeBackgroundColor(string colorName)
    {
        Color newColor = Color.white; // 기본값

        switch (colorName.ToLower())
        {
            case "red": newColor = Color.red; break;
            case "green": newColor = Color.green; break;
            case "blue": newColor = Color.blue; break;
        }

        Camera.main.backgroundColor = newColor;
    }

    // 안드로이드로 메시지 전송
    public void SendMessageToAndroid(string message)
    {
        Debug.Log("안드로이드로 메시지 전송: " + message);

#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                using (AndroidJavaObject context = activity.Call<AndroidJavaObject>("getApplicationContext"))
                {
                    // 브로드캐스트 인텐트 생성
                    AndroidJavaObject intent = new AndroidJavaObject("android.content.Intent");
                    intent.Call<AndroidJavaObject>("setAction", "com.yourdomain.UNITY_MESSAGE");
                    intent.Call<AndroidJavaObject>("putExtra", "message", message);
                    context.Call("sendBroadcast", intent);
                }
            }
        }
#else
        Debug.Log("안드로이드 플랫폼에서만 작동합니다.");
#endif
    }

    // UI 버튼에 연결할 메소드
    public void OnSendButtonClicked()
    {
        SendMessageToAndroid("Unity 버튼이 클릭됨!");
    }
}