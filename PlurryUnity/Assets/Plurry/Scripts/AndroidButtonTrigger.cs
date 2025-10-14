using UnityEngine;

public class AndroidButtonTrigger : MonoBehaviour
{
    private GameController gameController;
    private PlayerState playerState;
    private CharacterMove characterMove;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        gameController = GameObject.FindWithTag("GameController").GetComponent<GameController>();
        playerState = GameObject.FindWithTag("Player").GetComponent<PlayerState>();
        characterMove = GameObject.FindWithTag("Player").GetComponent<CharacterMove>();
        if (playerState == null)
            LogLS.Error("playerState Not Found!!");
        if (characterMove == null)
            LogLS.Error("characterMove Not Found!!");
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
            characterMove.SendMessage("SetcurrentEnterTrigger", gameObject.tag);
            string functionName = "Unity" + gameObject.tag + "TriggerEnter";
            gameController.SendMessage("SendCommendToAndroid", functionName);
        }
    }

    void OnTriggerExit(Collider other)
    { 
        if (other.tag == "Player")
        {
            characterMove.SendMessage("SetcurrentEnterTrigger", "");
            //string functionName = "Unity" + gameObject.tag + "TriggerExit";
            //gameController.SendMessage("SendCommendToAndroid", functionName);
        }
    }
}
