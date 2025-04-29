using System;
using System.Collections;
using UnityEngine;

public class CharacterMove : MonoBehaviour
{
    [SerializeField]
    private GameObject obj;
    Coroutine coroutine;
    bool isCo;


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
                coroutine = StartCoroutine(MoveCharacter(hit.point));
            }
        }
    }

    private IEnumerator MoveCharacter(Vector3 position)
    {
        isCo = true;
        while (obj.transform.position != position)
        {
            obj.transform.LookAt(new Vector3(position.x, obj.transform.position.y, position.z));
            obj.transform.position = Vector3.MoveTowards(obj.transform.position, new Vector3(position.x, obj.transform.position.y, position.z), Time.deltaTime);
            yield return null;
        }
    }
}
