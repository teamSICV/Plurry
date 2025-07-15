using System.Collections.Generic;
using UnityEngine;

public class RoofControll : MonoBehaviour
{
    [SerializeField]
    private GameObject player;
    [SerializeField]
    private Camera mainCamera;

    private HashSet<MeshRenderer> hiddenRoofs = new HashSet<MeshRenderer>();

    void Update()
    {
        CheckRoofVisibility();
    }

    private void CheckRoofVisibility()
    {
        if (player == null || mainCamera == null) return;

        Vector3 cameraPosition = mainCamera.transform.position;
        Vector3 playerPosition = player.transform.position;
        Vector3 direction = (playerPosition - cameraPosition).normalized;
        float distance = Vector3.Distance(cameraPosition, playerPosition);

        // 카메라에서 플레이어까지 레이캐스트
        RaycastHit[] hits = Physics.RaycastAll(cameraPosition, direction, distance);

        HashSet<MeshRenderer> currentlyHitRoofs = new HashSet<MeshRenderer>();

        // 현재 맞은 Roof들을 찾아서 숨기기
        foreach (RaycastHit hit in hits)
        {
            if (hit.collider.gameObject.tag == "Roof")
            {
                Debug.Log("Roof Detected!");
                MeshRenderer meshRenderer = hit.collider.GetComponent<MeshRenderer>();
                if (meshRenderer != null)
                {
                    currentlyHitRoofs.Add(meshRenderer);

                    if (!hiddenRoofs.Contains(meshRenderer))
                    {
                        meshRenderer.enabled = false;
                        hiddenRoofs.Add(meshRenderer);
                    }
                }
            }
        }

        // 더 이상 맞지 않는 Roof들을 다시 보이게 하기
        HashSet<MeshRenderer> roofsToShow = new HashSet<MeshRenderer>();
        foreach (MeshRenderer hiddenRoof in hiddenRoofs)
        {
            if (!currentlyHitRoofs.Contains(hiddenRoof))
            {
                hiddenRoof.enabled = true;
                roofsToShow.Add(hiddenRoof);
            }
        }

        // 다시 보이게 한 Roof들을 hiddenRoofs에서 제거
        foreach (MeshRenderer roofToShow in roofsToShow)
        {
            hiddenRoofs.Remove(roofToShow);
        }
    }
}
