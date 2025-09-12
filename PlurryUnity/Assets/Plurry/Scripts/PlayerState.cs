using System.Security.Cryptography.X509Certificates;
using UnityEngine;

public class PlayerState : MonoBehaviour
{
    private PRAniminstance animintance;
    private Transform PlayerTransform;
    private CameraMove cameraMove;
    private CharacterMove characterMove;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        animintance = GetComponent<PRAniminstance>();
        PlayerTransform = GetComponent<Transform>();
        cameraMove = GameObject.Find("Camera").GetComponent<CameraMove>();
        characterMove = GameObject.FindGameObjectWithTag("Player").GetComponent<CharacterMove>();
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    public void SetPlayerState(string StateName)
    {
        //string LogMessage = "SetPlayerState Begin - " + StateName;
        //GameObject.FindGameObjectWithTag("GameController").GetComponent<GameController>().SendMessage("SendDebugLogToAndroid", LogMessage);

        Vector3 targetPos;
        Vector3 moveVector;

        characterMove.bisCanPlayerInput = false;

        cameraMove.SendMessage("SetCameraState", StateName);

        switch (StateName)
        {
            case "Growing":
                //Debug.Log(StateName + "상태 시작");
                GetComponent<CharacterMove>().SendMessage("StopWalking");

                PlayerTransform.rotation = Quaternion.Euler(0, -40f, 0f);

                targetPos = new Vector3(2.74f, 0f, 2.6f);
                moveVector = targetPos - transform.position;
                GetComponent<CharacterController>().Move(moveVector);

                animintance.bisIdle = false;
                animintance.bisGrowing = true;
                animintance.bisItem = false;
                animintance.bisStory = false;

                break;

            case "Item":
                //Debug.Log(StateName + "상태 시작");
                GetComponent<CharacterMove>().SendMessage("StopWalking");

                PlayerTransform.rotation = Quaternion.Euler(0, -70f, 0f);

                targetPos = new Vector3(-2.43f, 0f, 2.22f);
                moveVector = targetPos - transform.position;
                GetComponent<CharacterController>().Move(moveVector);

                animintance.bisIdle = false;
                animintance.bisGrowing = false;
                animintance.bisItem = true;
                animintance.bisStory = false;

                break;

            case "Story":
                Debug.Log(StateName + "상태 시작");
                GetComponent<CharacterMove>().SendMessage("StopWalking");

                PlayerTransform.rotation = Quaternion.Euler(0, 81f, 0f);

                targetPos = new Vector3(2.5f, 0f, -0.5f);
                moveVector = targetPos - transform.position;
                GetComponent<CharacterController>().Move(moveVector);

                animintance.bisIdle = false;
                animintance.bisGrowing = false;
                animintance.bisItem = false;
                animintance.bisStory = true;

                break;

            default:
                break;
        }

    }

    public void EndPlayerState()
    {
        animintance.bisIdle = true;
        animintance.bisGrowing = false;
        animintance.bisItem = false;
        animintance.bisStory = false;

        cameraMove.SendMessage("EndCameraState");

        Invoke("EnablePlayerInput", 0.5f);
    }

    private void EnablePlayerInput()
    {
        //string LogMessage = "EnablePlayerInput Begin";
        //GameObject.FindGameObjectWithTag("GameController").GetComponent<GameController>().SendMessage("SendDebugLogToAndroid", LogMessage);

        characterMove.bisCanPlayerInput = true;
    }
}


