using UnityEngine;

public class PRAniminstance : MonoBehaviour
{
    private Animator animator;
    public bool bisIdle = true;
    private int isIdleHash;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        animator = GetComponent<Animator>();
        //ownerController = GetComponent<CharacterController>();

        isIdleHash = Animator.StringToHash("IsIdle");
    }

    // Update is called once per frame
    void Update()
    {
        animator.SetBool(isIdleHash, bisIdle);
    }
}
