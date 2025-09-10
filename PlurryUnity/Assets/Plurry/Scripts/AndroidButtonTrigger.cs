using UnityEngine;

public class AndroidButtonTrigger : MonoBehaviour
{
    private GameObject gameController;
    private PlayerState playerState;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        gameController = GameObject.FindWithTag("GameController");
        playerState = GameObject.FindWithTag("Player").GetComponent<PlayerState>();
        if (playerState == null)
            Debug.LogError("playerState Not Found!!");
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    void OnTriggerEnter(Collider other)
    {
        if(other.tag == "Player")
        {
            playerState.SendMessage("SetPlayerState", gameObject.tag);
            string functionName = "Unity" + gameObject.tag + "TriggerEnter";
            //Debug.Log("OnTriggerEnter Beggin : " + functionName);
            gameController.GetComponent<GameController>().SendMessage("SendCommendToAndroid", functionName);
            
        }
    }

    void OnTriggerExit(Collider other)
    {
        if (other.tag == "Player")
        {
            playerState.SendMessage("EndPlayerState");
            string functionName = "Unity" + gameObject.tag + "TriggerExit";
            //Debug.Log("OnTriggerEnd Beggin : " + functionName);
            gameController.GetComponent<GameController>().SendMessage("SendCommendToAndroid", functionName);
        }
    }
}
