using UnityEngine;

public class AndroidButtonTrigger : MonoBehaviour
{
    private GameController gameController;
    private PlayerState playerState;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        gameController = GameObject.FindWithTag("GameController").GetComponent<GameController>();
        playerState = GameObject.FindWithTag("Player").GetComponent<PlayerState>();
        if (playerState == null)
            LogLS.Error("playerState Not Found!!");
    }

    // Update is called once per frame
    void Update()
    {
        
    }

    void OnTriggerEnter(Collider other)
    {
        if (other.tag == "Player")
        {
            playerState.SendMessage("SetPlayerState", gameObject.tag);
            string functionName = "Unity" + gameObject.tag + "TriggerEnter";
            gameController.SendMessage("SendCommendToAndroid", functionName);
            
        }
    }

    void OnTriggerExit(Collider other)
    { 
        if (other.tag == "Player")
        {
            string functionName = "Unity" + gameObject.tag + "TriggerExit";
            gameController.SendMessage("SendCommendToAndroid", functionName);
        }
    }
}
