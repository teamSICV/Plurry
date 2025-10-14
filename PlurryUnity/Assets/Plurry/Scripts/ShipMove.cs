using UnityEngine;
using UnityEngine.UIElements;

public class ShipMove : MonoBehaviour
{
    //private Transform ShipTransform;

    //// Start is called once before the first execution of Update after the MonoBehaviour is created
    //void Start()
    //{
    //    ShipTransform = GetComponent<Transform>();
    //}

    //// Update is called once per frame
    //void Update()
    //{

    //}

    //public void SetShipRotate(float inYRotate)
    //{
    //    ShipTransform.rotation = Quaternion.Euler(0, inYRotate, 0f);
    //}

    private Quaternion initialRotation;
    [SerializeField]
    private GameObject mark;
    private Vector3 newPosition;
    private Vector3 postPosition;

    void Start()
    {
        // 자이로 센서가 지원되는지 확인
        if (SystemInfo.supportsGyroscope)
        {
            Input.gyro.enabled = true;
            initialRotation = transform.rotation;
        }
        else
        {
            Debug.Log("자이로 센서를 지원하지 않는 기기입니다.");
        }

#if UNITY_EDITOR
        InvokeRepeating("MoveShip", 0f, 2f);
#endif
    }

    void Update()
    {
        if (Input.gyro.enabled)
        {
            // 기기 회전 값을 쿼터니언으로 가져옴
            Quaternion gyroAttitude = Input.gyro.attitude;

            // 유니티 좌표계에 맞게 쿼터니언 변환 및 보정
            // 이 변환으로 기기의 Z축(YAW) 회전이 유니티의 Y축(YAW) 회전으로 매핑됨
            Quaternion gyroRotation = Quaternion.Euler(90, 0, 0) * new Quaternion(-gyroAttitude.x, -gyroAttitude.y, gyroAttitude.z, gyroAttitude.w);

            // Y축(YAW) 회전 값만 추출
            Quaternion yRotation = Quaternion.Euler(0, gyroRotation.eulerAngles.y, 0);

            // 오브젝트에 회전 적용
            transform.rotation = initialRotation * yRotation;
        }

        transform.position = Vector3.MoveTowards(transform.position, newPosition, 8f * Time.deltaTime);
    }

    void MoveShip()
    {
        // mark 오브젝트를 현재 위치에 생성 (y값은 3으로)
        postPosition = transform.position;
        Vector3 markPos = new Vector3(postPosition.x, 3f, postPosition.z);
        GameObject markInstance = Instantiate(mark, markPos, Quaternion.identity);
        ParticleSystem ps = markInstance.GetComponentInChildren<ParticleSystem>();
        if (ps != null)
        {
            ps.Play();
        }

        // 현재 오브젝트를 바라보는 방향으로 10m 전진 후 y값은 0으로
        newPosition += transform.forward * 20f;
    }
}
