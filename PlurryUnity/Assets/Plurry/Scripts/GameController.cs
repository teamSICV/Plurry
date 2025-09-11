using UnityEngine;

public class GameController : MonoBehaviour
{
    private string displayText = "Unity Callback test";
    public GUISkin skin;
    string androidFunctionName;
    private PlayerState playerState;

    void Start()
    {
        androidFunctionName = "";
        playerState = GameObject.FindGameObjectWithTag("Player").GetComponent<PlayerState>();
    }


//Call Out Section
    public void SendCommendToAndroid(string functionName)
    {
        if(functionName != "")
        {
            androidFunctionName = functionName; 
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        CallAndroidFunction();
#else
        Debug.Log("안드로이드 플랫폼에서만 작동합니다. : " + androidFunctionName);
#endif

        androidFunctionName = "";
    }

    private void CallAndroidFunction()
    {
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                activity.Call(androidFunctionName);
            }
        }
    }

    public void SendCommendToAndroidWithVector2(string functionName, Vector2 parameter)
    {
        displayText = "SendCommendToAndroidWithVector2 Called";
        if (functionName != "")
        {
            androidFunctionName = functionName;
        }
#if UNITY_ANDROID && !UNITY_EDITOR
    CallAndroidFunctionWithVector2(parameter);
#else
        Debug.Log($"안드로이드 플랫폼에서만 작동합니다. : {androidFunctionName}, 파라미터: ({parameter.x}, {parameter.y})");
#endif
        androidFunctionName = "";
    }

    private void CallAndroidFunctionWithVector2(Vector2 parameter)
    {
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                // float 배열로 전달하거나
                activity.Call(androidFunctionName, parameter.x, parameter.y);

                // 또는 문자열로 전달
                // activity.Call(androidFunctionName, $"{parameter.x},{parameter.y}");
            }
        }
    }



    //Call Back Section
    private void UnityProcessGrowing()
    {
        //displayText = "Growing success";
        playerState.SendMessage("EndPlayerState");
    }

    private void UnityProcessStory()
    {
        //displayText = "Story success";
        playerState.SendMessage("EndPlayerState");
    }

    private void UnityProcessRanking()
    {
        //displayText = "Ranking success";
        playerState.SendMessage("EndPlayerState");
    }

    private void UnityProcessItem()
    {
        //displayText = "Item success";
        playerState.SendMessage("EndPlayerState");
    }


    //Debugging Section

    void OnGUI()
    {
        GUI.skin = skin;
        GUI.Label(new Rect(Screen.width / 2 - 50, Screen.height*4 / 5, 100, 50), displayText, "Test");
    }
}