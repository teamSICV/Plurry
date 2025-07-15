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

        // ī�޶󿡼� �÷��̾���� ����ĳ��Ʈ
        RaycastHit[] hits = Physics.RaycastAll(cameraPosition, direction, distance);

        HashSet<MeshRenderer> currentlyHitRoofs = new HashSet<MeshRenderer>();

        // ���� ���� Roof���� ã�Ƽ� �����
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

        // �� �̻� ���� �ʴ� Roof���� �ٽ� ���̰� �ϱ�
        HashSet<MeshRenderer> roofsToShow = new HashSet<MeshRenderer>();
        foreach (MeshRenderer hiddenRoof in hiddenRoofs)
        {
            if (!currentlyHitRoofs.Contains(hiddenRoof))
            {
                hiddenRoof.enabled = true;
                roofsToShow.Add(hiddenRoof);
            }
        }

        // �ٽ� ���̰� �� Roof���� hiddenRoofs���� ����
        foreach (MeshRenderer roofToShow in roofsToShow)
        {
            hiddenRoofs.Remove(roofToShow);
        }
    }
}
