using UnityEngine;

public class SplashEndNotify : MonoBehaviour
{
    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        GameController gc = GetComponent<GameController>();
        if(gc != null )
        {
            gc.SendMessage("SendCommendToAndroid", "EndUnitySplash");
        }
    }

    // Update is called once per frame
    void Update()
    {
        
    }
}
