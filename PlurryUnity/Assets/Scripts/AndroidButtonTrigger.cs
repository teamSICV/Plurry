using UnityEngine;

public class AndroidButtonTrigger : MonoBehaviour
{
    private GameObject gameController;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        gameController = GameObject.FindWithTag("GameController");
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    void OnTriggerEnter(Collider other)
    {
        Debug.Log("OnTriggerEnter Beggin");
        gameController.GetComponent<GameController>().SendMessage("SendMessageToAndroid");
    }
}
