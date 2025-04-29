using System.Collections;
using System.Collections.Generic;
using Unity.VisualScripting;
using UnityEngine;

public class CameraMove : MonoBehaviour
{
    //Touch Input
    private Vector2 nowtouchPos, pretouchPos;
    private float zoomPreTouchDistance, zoomNowTouchDistance;

    //Camera Follow
    public Transform objectToFollow;
    [SerializeField]
    private float followSpeed = 10f;
    [SerializeField]
    private float touchYSensitivity = 0.2f;
    [SerializeField]
    private float touchXSensitivity = 0.1f;
    [SerializeField]
    private float zoomSensitivity = 1f;
    [SerializeField]
    private float clampAngle = 70f;

    private float rotX;
    private float rotY;

    public Camera mainCameraDist;
    public Transform mainCameraPos;
    public Vector3 dir;
    public Vector3 rayDir;
    public float dist;

    [SerializeField]
    private float minDist;
    [SerializeField]
    private float maxDist;
    [SerializeField]
    private float smoothness = 10;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        this.rotX = this.gameObject.transform.localRotation.eulerAngles.x;
        this.rotY = this.gameObject.transform.localRotation.eulerAngles.y;

        this.dir = this.mainCameraPos.localPosition.normalized;
        this.dist = this.mainCameraPos.localPosition.magnitude;
    }

    // Update is called once per frame
    void Update()
    {
        //Get Input
        if (Input.touchCount == 1)
        {
            UnityEngine.Touch touch = Input.GetTouch(0);
            if (touch.phase == TouchPhase.Moved)
            {
                MoveCamera(touch.deltaPosition);
            }
        }

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

    private void MoveCamera(Vector2 inputPos)
    {
        this.rotY += inputPos.x * this.touchXSensitivity + Time.deltaTime;
        Quaternion rot = Quaternion.Euler(this.rotX, this.rotY, 0);
        this.gameObject.transform.rotation = rot;
    }

    private void MoveDistance(float distance)
    {
        this.mainCameraDist.GetComponent<Camera>().fieldOfView -= distance * this.zoomSensitivity;
        this.mainCameraDist.GetComponent<Camera>().fieldOfView = Mathf.Clamp(this.mainCameraDist.GetComponent<Camera>().fieldOfView, minDist, maxDist);
    }
}
