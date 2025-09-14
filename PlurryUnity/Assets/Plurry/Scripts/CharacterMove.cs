using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Playables;

# if UNITY_EDITOR
using UnityEditor.Experimental.GraphView;
# endif

public class CharacterMove : MonoBehaviour
{
    [SerializeField]
    private GameObject pinPoint;
    private GameObject player;
    private Coroutine coroutine;
    private bool isCo;
    private PRAniminstance animintance;
    private CharacterController characterController;

    public bool bisCanPlayerInput = true;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        player = this.gameObject;
        characterController = GetComponent<CharacterController>();
        animintance = GetComponent<PRAniminstance>();
        isCo = false;
    }

    // Update is called once per frame
    void Update()
    {
        if(bisCanPlayerInput)
        {
            //Get Input
            if (Input.touchCount == 1)
            {
                UnityEngine.Touch touch = Input.GetTouch(0);
                if (touch.phase == TouchPhase.Began)
                {
                    TouchRay(touch.position);
                }
            }
        }
        else
        {
#if UNITY_EDITOR
            //For Debug
            if (Input.touchCount == 1)
            {
                GameObject.FindGameObjectWithTag("Player").GetComponent<PlayerState>().SendMessage("EndPlayerState");
            }
#endif
        }


        if (isCo)
        {
            animintance.bisIdle = false;
        }
        else
        {
            animintance.bisIdle = true;
        }
    }

    public void StopWalking()
    {
        GameObject[] pinObjects = GameObject.FindGameObjectsWithTag("Mark");
        foreach (GameObject pinObject in pinObjects)
        {
            Destroy(pinObject);
        }

        if (isCo)
        {
            isCo = false;
            StopCoroutine(coroutine);
        }
    }

    private void TouchRay(Vector2 position)
    {
        Ray ray = Camera.main.ScreenPointToRay(position);
        RaycastHit hit;
        int floorLayer = LayerMask.GetMask("Floor", "Player");

        if (Physics.Raycast(ray, out hit, Mathf.Infinity, floorLayer))
        {
            if (hit.transform.tag == "Player")
            {
                StopWalking();
                bisCanPlayerInput = false;

                Vector3 cameraPosition = Camera.main.transform.position;
                Vector3 playerPosition = hit.transform.position;

                Vector3 directionToCamera = cameraPosition - playerPosition;
                directionToCamera.y = 0; 

                if (directionToCamera != Vector3.zero)
                {
                    player.transform.rotation = Quaternion.LookRotation(directionToCamera);
                }

                GameObject.FindGameObjectWithTag("Script").GetComponent<FloatCharacterScript>().SendMessage("SendScriptLocation");
                animintance.SendMessage("PlayGreeting");
            }
            else if (hit.transform.tag == "Floor")
            {
                StopWalking();

                coroutine = StartCoroutine(MoveCharacter(hit.point));
                GameObject.Instantiate(pinPoint, hit.point, Quaternion.Euler(0f, 0f, 0f));
            }
        }
    }

    private IEnumerator MoveCharacter(Vector3 position)
    {
        isCo = true;

        Vector3 targetPosition = new Vector3(position.x, player.transform.position.y, position.z);

        while (Vector3.Distance(new Vector3(player.transform.position.x, 0, player.transform.position.z),
                                 new Vector3(targetPosition.x, 0, targetPosition.z)) > 0.1f)
        {
            Vector3 lookDirection = (targetPosition - player.transform.position).normalized;
            lookDirection.y = 0; 
            player.transform.rotation = Quaternion.LookRotation(lookDirection);

            Vector3 direction = (targetPosition - player.transform.position).normalized;
            Vector3 moveVector = direction * Time.deltaTime;

            characterController.Move(moveVector);
            yield return null;
        }

        isCo = false;
    }
}
