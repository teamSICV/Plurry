using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class RoofControll : MonoBehaviour
{
    [SerializeField]
    private GameObject player;
    [SerializeField]
    private Camera mainCamera;
    private float checkInterval = 0.5f; // 체크 간격 (초)

    private bool bisRoof1Visible = true;
    private bool bisRoof2Visible = true;

    [SerializeField]
    private GameObject Roof1;

    [SerializeField]
    private GameObject Roof2;

    void Start()
    {
        StartCoroutine(CheckRoofVisibilityRoutine());
    }

    private IEnumerator CheckRoofVisibilityRoutine()
    {
        while (true)
        {
            CheckRoofVisibility();
            yield return new WaitForSeconds(checkInterval);
        }
    }

    private void CheckRoofVisibility()
    {
        if (player == null || mainCamera == null) return;

        Vector3 cameraPosition = mainCamera.transform.position;
        Vector3 playerPosition = player.transform.position;
        Vector3 direction = (playerPosition - cameraPosition).normalized;
        float distance = Vector3.Distance(cameraPosition, playerPosition);

        // 카메라에서 플레이어까지 레이캐스트
        int roofLayer = LayerMask.GetMask("Roof");
        RaycastHit[] hits = Physics.RaycastAll(cameraPosition, direction, distance, roofLayer);

        if (hits.Length == 0)
        {
            Roof1.SetActive(true);
            bisRoof1Visible = true;
            Roof2.SetActive(true);
            bisRoof2Visible = true;

            return;
        }

        foreach (RaycastHit hit in hits)
        {
            if ((hit.collider.gameObject.name == "Roof1Colider") && bisRoof1Visible)
            {
                Roof1.SetActive(false);
                bisRoof1Visible = false;
            }
            else if ((hit.collider.gameObject.name == "Roof2Colider") && bisRoof2Visible)
            {
                Roof2.SetActive(false);
                bisRoof2Visible = false;
            }
        }

    }
}