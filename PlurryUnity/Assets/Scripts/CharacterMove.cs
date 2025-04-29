using System;
using System.Collections;
using UnityEngine;

public class CharacterMove : MonoBehaviour
{
    [SerializeField]
    private GameObject player;
    Coroutine coroutine;
    bool isCo;
    [SerializeField]
    private GameObject pinPoint;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        
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
    }

    private void TouchRay(Vector2 position)
    {
        Ray ray = Camera.main.ScreenPointToRay(position);
        RaycastHit hit;

        if (Physics.Raycast(ray, out hit))
        {
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
        while (player.transform.position != position)
        {
            player.transform.LookAt(new Vector3(position.x, player.transform.position.y, position.z));
            player.transform.position = Vector3.MoveTowards(player.transform.position, new Vector3(position.x, player.transform.position.y, position.z), Time.deltaTime);
            yield return null;
        }
    }
}
