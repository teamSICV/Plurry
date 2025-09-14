using UnityEngine;

public class GreetEndNotify : StateMachineBehaviour
{
    public override void OnStateExit(Animator animator, AnimatorStateInfo stateInfo, int layerIndex)
    {
        CharacterMove characterMove = GameObject.FindGameObjectWithTag("Player").GetComponent<CharacterMove>();
        if (characterMove != null)
        {
            characterMove.bisCanPlayerInput = true;
        }
    }
}
