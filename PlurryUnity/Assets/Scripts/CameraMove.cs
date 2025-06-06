using System.Collections;
using System.Collections.Generic;
using Unity.VisualScripting;
using UnityEngine;

public class CameraMove : MonoBehaviour
{
    //Touch Input
    private float zoomPreTouchDistance, zoomNowTouchDistance;

    //Camera Follow
    public Transform objectToFollow;
    [SerializeField]
    private float followSpeed = 10f;
    [SerializeField]
    private float zoomSensitivity = 1f;

    public Camera mainCameraDist;
    public Transform mainCameraPos;
    public Vector3 dir;
    public Vector3 rayDir;
    public float dist;
    [SerializeField]
    private float smoothness = 10;


    [SerializeField]
    private float minDist;
    [SerializeField]
    private float maxDist;
    [SerializeField]
    private float fieldOfView;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        this.dir = this.mainCameraPos.localPosition.normalized;
        this.dist = this.mainCameraPos.localPosition.magnitude;

        fieldOfView = this.mainCameraDist.GetComponent<Camera>().fieldOfView;
    }

    // Update is called once per frame
    void Update()
    {
        //Get Input
        if (Input.touchCount == 2)
        {
            UnityEngine.Touch touch1 = Input.GetTouch(0);
            UnityEngine.Touch touch2 = Input.GetTouch(1);
            if (touch1.phase == TouchPhase.Moved)
            {
                Vector2 touchZeroPrevPos = touch1.position - touch1.deltaPosition;
                Vector2 touchOnePrevPos = touch2.position - touch2.deltaPosition;
                zoomPreTouchDistance = (touchZeroPrevPos - touchOnePrevPos).magnitude;
                zoomNowTouchDistance = (touch1.position - touch2.position).magnitude;
                MoveDistance(zoomNowTouchDistance - zoomPreTouchDistance);
                zoomPreTouchDistance = zoomNowTouchDistance;
            }
        }
    }

    private void LateUpdate()
    {
        this.transform.position = Vector3.MoveTowards(this.gameObject.transform.position, this.objectToFollow.position, this.followSpeed * Time.deltaTime);
        this.mainCameraPos.localPosition = Vector3.Lerp(this.mainCameraPos.localPosition, this.dir * this.dist, Time.deltaTime * this.smoothness);
    }

    private void MoveDistance(float distance)
    {
        fieldOfView -= distance * this.zoomSensitivity;
        fieldOfView = Mathf.Clamp(fieldOfView, minDist, maxDist);
        this.mainCameraDist.GetComponent<Camera>().fieldOfView = fieldOfView;
    }
}
