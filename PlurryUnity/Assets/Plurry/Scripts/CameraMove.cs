using System.Collections;
using System.Collections.Generic;
using Unity.VisualScripting;
using UnityEngine;
//using static UnityEditorInternal.VersionControl.ListControl;

public class CameraMove : MonoBehaviour
{
    //Camera Follow
    public Transform objectToFollow;
    [SerializeField]
    private float followSpeed = 10f;
    public Transform mainCameraTrans;
    private bool bisState = false;


    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
    }

    // Update is called once per frame
    void Update()
    {
    }

    private void LateUpdate()
    {
        if (!bisState)
        {
            this.transform.position = Vector3.MoveTowards(this.gameObject.transform.position, this.objectToFollow.position, this.followSpeed * Time.deltaTime);
        }
    }

    public void SetCameraState(string StateName)
    {
        bisState = true;

        switch (StateName)
        {
            case "Growing":
                transform.position = new Vector3(1.06f, 1.38f, 1.08f);
                mainCameraTrans.localRotation = Quaternion.Euler(-20f, 68f, -58.8f);
                mainCameraTrans.localPosition = new Vector3(-1.21f, -0.12f, -0.2f);

                break;

            case "Item":
                transform.position = new Vector3(-1.99f, 1.38f, 1.52f);
                mainCameraTrans.localRotation = Quaternion.Euler(-36.63f, 2.6f, -3f);
                mainCameraTrans.localPosition = new Vector3(-1f, -1.6f, -1.25f);

                break;

            case "Story":
                transform.position = new Vector3(1.61f, 1.4f, -1.3f);
                mainCameraTrans.localRotation = Quaternion.Euler(37.7f, 103.21f, -57.57f);
                mainCameraTrans.localPosition = new Vector3(-1.2f, 2.8f, 1.48f);

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
