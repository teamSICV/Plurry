using UnityEngine;

public class FloatCharacterScript : MonoBehaviour
{
    GameObject scriptTarget;
    GameController gameController;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        scriptTarget = GameObject.FindGameObjectWithTag("Script");
        gameController = GameObject.FindGameObjectWithTag("GameController").GetComponent<GameController>();
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    public void SendScriptLocation()
    {
        Vector3 screenPosition = Camera.main.WorldToScreenPoint(scriptTarget.transform.position);
        Vector2 paramVector = new Vector2(screenPosition.x, screenPosition.y);
        gameController.SendMessage("SendCommendToAndroid", "UnityPopUpScript");
    }
}
