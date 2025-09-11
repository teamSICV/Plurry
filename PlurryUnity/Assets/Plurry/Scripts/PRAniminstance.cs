using UnityEngine;

public class PRAniminstance : MonoBehaviour
{
    private Animator animator;
    public bool bisIdle = true;
    public bool bisGrowing = false;
    public bool bisItem = false;
    private int isIdleHash;
    private int isGrowingHash;
    private int isItemHash;
    private int greetingHash;

    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        animator = GetComponent<Animator>();
        //ownerController = GetComponent<CharacterController>();

        isIdleHash = Animator.StringToHash("IsIdle");
        isGrowingHash = Animator.StringToHash("IsGrowing");
        isItemHash = Animator.StringToHash("IsItem");
        greetingHash = Animator.StringToHash("Greeting");
    }

    // Update is called once per frame
    void Update()
    {
        animator.SetBool(isIdleHash, bisIdle);
        animator.SetBool(isGrowingHash, bisGrowing);
        animator.SetBool(isItemHash, bisItem);
    }

    public void PlayGreeting()
    {
        animator.SetTrigger(greetingHash);
    }
}
