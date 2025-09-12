using UnityEngine;

public class GreetEndNotify : StateMachineBehaviour
{
    public override void OnStateExit(Animator animator, AnimatorStateInfo stateInfo, int layerIndex)
    {
        //Debug.Log("Greeting End Notify");
        CharacterMove characterMove = GameObject.FindGameObjectWithTag("Player").GetComponent<CharacterMove>();
        if (characterMove != null)
        {
            characterMove.bisCanPlayerInput = true;
        }
    }
}
