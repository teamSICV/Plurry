using System.Collections;
using System.Collections.Generic;
using Unity.VisualScripting;
using UnityEngine;
//using static UnityEditorInternal.VersionControl.ListControl;

public class CameraMove : MonoBehaviour
{
    //Touch Input
    //private float zoomPreTouchDistance, zoomNowTouchDistance;

    //Camera Follow
    public Transform objectToFollow;
    [SerializeField]
    private float followSpeed = 10f;

    //private float zoomSensitivity = 1f;

    //public Camera mainCameraDist;
    public Transform mainCameraTrans;
    //public Vector3 dir;
    //public Vector3 rayDir;
    //public float dist;

    //private float smoothness = 10;

    //private float minDist = -5f;
    //private float maxDist = -10f;
    //private float cameraDist;


    private bool bisState = false;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        //this.dir = mainCameraTrans.localPosition.normalized;
        //this.dist = mainCameraTrans.localPosition.magnitude;

        //cameraDist = mainCameraDist.GetComponent<Transform>().position.z;
    }

    // Update is called once per frame
    void Update()
    {
        //Get Input
        //if (Input.touchCount == 2)
        //{
        //    UnityEngine.Touch touch1 = Input.GetTouch(0);
        //    UnityEngine.Touch touch2 = Input.GetTouch(1);
        //    if (touch1.phase == TouchPhase.Moved)
        //    {
        //        Vector2 touchZeroPrevPos = touch1.position - touch1.deltaPosition;
        //        Vector2 touchOnePrevPos = touch2.position - touch2.deltaPosition;
        //        zoomPreTouchDistance = (touchZeroPrevPos - touchOnePrevPos).magnitude;
        //        zoomNowTouchDistance = (touch1.position - touch2.position).magnitude;
        //        MoveDistance(zoomNowTouchDistance - zoomPreTouchDistance);
        //        zoomPreTouchDistance = zoomNowTouchDistance;
        //    }
        //}
    }

    private void LateUpdate()
    {
        if (!bisState)
        {
            this.transform.position = Vector3.MoveTowards(this.gameObject.transform.position, this.objectToFollow.position, this.followSpeed * Time.deltaTime);
            //mainCameraTrans.localPosition = Vector3.Lerp(mainCameraTrans.localPosition, this.dir * this.dist, Time.deltaTime * this.smoothness);
        }
    }

    //private void MoveDistance(float distance)
    //{
    //    //cameraDist -= distance * this.zoomSensitivity;
    //    //cameraDist = Mathf.Clamp(cameraDist, minDist, maxDist);
    //    //mainCameraPos.position = new Vector3(0, 0, cameraDist);
    //}

    public void SetCameraState(string StateName)
    {
        bisState = true;

        switch (StateName)
        {
            case "Growing":
                //Debug.Log(StateName + "카메라 상태 시작");

                transform.position = new Vector3(1.06f, 1.38f, 1.08f);
                mainCameraTrans.localRotation = Quaternion.Euler(-20f, 68f, -58.8f);
                mainCameraTrans.localPosition = new Vector3(-1.21f, -0.12f, -0.2f);

                break;

            case "Item":
                //Debug.Log(StateName + "카메라 상태 시작");

                transform.position = new Vector3(-1.99f, 1.38f, 1.52f);
                mainCameraTrans.localRotation = Quaternion.Euler(-36.63f, 2.6f, -3f);
                mainCameraTrans.localPosition = new Vector3(-1f, -1.6f, -1.25f);

                break;

            default:
                break;
        }
    }

    public void EndCameraState()
    {
        mainCameraTrans.localPosition = new Vector3(0f, 0f, -10f);
        mainCameraTrans.localRotation = Quaternion.Euler(0f, 0f, 0f);

        bisState = false;
    }
}
