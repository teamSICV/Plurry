using UnityEngine;
using UnityEngine.SceneManagement;

public class GameController : MonoBehaviour
{
    string androidFunctionName;

    //Raising Main
    private PlayerState playerState;

    //Going Walk
    private ShipMove ShipMove;
    private Safety Sea;



    //GUI Debug
    //public GUISkin skin;
    //public string debugText = "Debug Test";

    public void Start()
    {
        //LogLS.Log("Begin");

        androidFunctionName = "";
        Invoke("EndUnityLoadingScene", 1f);

        //Raising Main
        if(SceneManager.GetActiveScene().name == "RaisingMain")
        {
            if (GameObject.FindGameObjectWithTag("Player"))
            {
                playerState = GameObject.FindGameObjectWithTag("Player").GetComponent<PlayerState>();
            }
        }


        //Going Walk
        if (SceneManager.GetActiveScene().name == "GoingWalk")
        {
            if (GameObject.FindGameObjectWithTag("Ship"))
            {
                ShipMove = GameObject.FindGameObjectWithTag("Ship").GetComponent<ShipMove>();
                Sea = GameObject.FindGameObjectWithTag("Sea").GetComponent<Safety>();

            }
        }
    }

    //public void Update()
    //{
    //    //Get Input Test
    //    if (Input.touchCount == 1)
    //    {
    //        LogLS.Log("Unity Touch Called");
    //        debugText = "Touch : " + true;
    //    }
    //    else
    //    {
    //        debugText = "Touch : " + false;
    //    }
    //}

    public void EndUnityLoadingScene()
    {
        //LogLS.Log("Begin - 08:19");
        SendCommendToAndroid("EndUnityLoadingScene");
    }


//Call Out Section
    public void SendCommendToAndroid(string functionName)
    {
        //LogLS.Log(functionName);
        if(functionName != "")
        {
            androidFunctionName = functionName; 
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        CallAndroidFunction();
#else
        LogLS.Log("안드로이드 플랫폼에서만 작동합니다. : " + androidFunctionName);
#endif

        androidFunctionName = "";
    }

    private void CallAndroidFunction()
    {
        using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            using (AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                activity.Call("CalledFunctionFromUnity", androidFunctionName);
            }
        }
    }

    //Call Back Section
    void LoadUnityScene(string sceneName)
    {
        //LogLS.Log("Begin");
        SceneManager.LoadScene(sceneName);
    }

    private void UnityProcessGrowing()
    {
        if (SceneManager.GetActiveScene().name == "RaisingMain")
        {
            //displayText = "Growing success";
            playerState.SendMessage("EndPlayerState");
        }
    }

    private void UnityProcessStory()
    {
        if (SceneManager.GetActiveScene().name == "RaisingMain")
        {
            //displayText = "Story success";
            playerState.SendMessage("EndPlayerState");
        }
    }

    private void UnityProcessRanking()
    {
        if (SceneManager.GetActiveScene().name == "RaisingMain")
        {
            //displayText = "Ranking success";
            playerState.SendMessage("EndPlayerState");
        }
    }

    private void UnityProcessItem()
    {
        if (SceneManager.GetActiveScene().name == "RaisingMain")
        {
            //displayText = "Item success";
            playerState.SendMessage("EndPlayerState");
        }
    }

    public void SetShipRotate(float inYRotate)
    {
        if (SceneManager.GetActiveScene().name == "GoingWalk")
        {
            ShipMove.SendMessage("SetShipRotate", inYRotate);
        }
    }

    void MoveShip()
    {
        if (SceneManager.GetActiveScene().name == "GoingWalk")
        {
            ShipMove.SendMessage("MoveShip");
        }
    }

    void SetSafety(string safety)
    {
        LogLS.Log("Begin");
        if (SceneManager.GetActiveScene().name == "GoingWalk")
        {
            Sea.SendMessage("SetSafety", safety);
        }
    }


    //GUI Debug
    //void OnGUI()
    //{
    //    if (skin != null) GUI.skin = skin;
    //    // 화면 중앙 계산
    //    float centerX = Screen.width / 2f - 150f; // 라벨 폭의 절반만큼 빼기
    //    float centerY = Screen.height / 2f - 100f;

    //    GUI.Label(new Rect(centerX, centerY, 300, 30), debugText, skin.GetStyle("Test"));
    //}
}