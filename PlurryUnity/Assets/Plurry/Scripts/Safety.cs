using UnityEngine;

public class Safety : MonoBehaviour
{
    [SerializeField]
    private Material SafeMaterial;
    [SerializeField]
    private Material CautionMaterial;
    [SerializeField]
    private Material DangerMaterial;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    void SetSafety(string safety)
    {
        LogLS.Log("Begin" + safety);
        switch (safety)
        {
            case "SAFE":
                GetComponent<Renderer>().material = SafeMaterial;
                break;
            case "CAUTION":
                GetComponent<Renderer>().material = CautionMaterial;
                break;
            case "DANGER":
                GetComponent<Renderer>().material = DangerMaterial;
                break;
            default:
                LogLS.Error("No case! : " + safety);
                break;
        }
    }
}
