using System;
using System.Collections;
using UnityEditor.Experimental.GraphView;
using UnityEngine;

public class CharacterMove : MonoBehaviour
{
    [SerializeField]
    private GameObject player;
    [SerializeField]
    private GameObject pinPoint;
    private Coroutine coroutine;
    private bool isCo;
    private PRAniminstance animintance;
    private CharacterController characterController;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        characterController = player.GetComponent<CharacterController>();
        animintance = GetComponent<PRAniminstance>();
        isCo = false;
    }

    // Update is called once per frame
    void Update()
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

        if (isCo)
        {
            animintance.bisIdle = false;
        }
        else
        {
            animintance.bisIdle = true;
        }
    }

    private void TouchRay(Vector2 position)
    {
        Ray ray = Camera.main.ScreenPointToRay(position);
        RaycastHit hit;
        int floorLayer = LayerMask.GetMask("Floor");

        //if (Physics.Raycast(ray, out hit))
        if (Physics.Raycast(ray, out hit, Mathf.Infinity, floorLayer))
        {
            //Debug.Log("Raycast Hitted : " + hit.transform.tag);
            if (hit.transform.tag == "Floor")
            {
                if (isCo)
                {
                    isCo = false;
                    StopCoroutine(coroutine);
                }
                GameObject pinObject = GameObject.Find("Mark(Clone)");
                Destroy(pinObject);
                coroutine = StartCoroutine(MoveCharacter(hit.point));
                GameObject.Instantiate(pinPoint, hit.point, Quaternion.Euler(0f, 0f, 0f));
            }
        }
    }

    private IEnumerator MoveCharacter(Vector3 position)
    {
        isCo = true;
        /*        while (player.transform.position != position)
                {
                    player.transform.LookAt(new Vector3(position.x, player.transform.position.y, position.z));
                    player.transform.position = Vector3.MoveTowards(player.transform.position, new Vector3(position.x, player.transform.position.y, position.z), Time.deltaTime);
                    yield return null;
                }*/

        Vector3 targetPosition = new Vector3(position.x, player.transform.position.y, position.z);
        //Debug.Log(Vector3.Distance(new Vector3(player.transform.position.x, 0, player.transform.position.z), new Vector3(targetPosition.x, 0, targetPosition.z)));

        while (Vector3.Distance(new Vector3(player.transform.position.x, 0, player.transform.position.z),
                                 new Vector3(targetPosition.x, 0, targetPosition.z)) > 0.1f)
        {
            player.transform.LookAt(targetPosition);

            Vector3 direction = (targetPosition - player.transform.position).normalized;
            Vector3 moveVector = direction * Time.deltaTime;

            characterController.Move(moveVector);
            yield return null;
        }

        GameObject pinObject = GameObject.Find("Mark(Clone)");
        Destroy(pinObject);
        isCo = false;
    }
}
