<script>
    import {createEventDispatcher} from "svelte";

    let dispatch = createEventDispatcher();
    export let visible = false;
</script>
<svelte:window on:keydown={(e)=> e["keyCode"] ===27 && (visible=false) }/>
{#if visible}
    <div class="modal is-active">
        <div class="modal-background"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title">
                    <slot name="title">
                        Empty
                    </slot>
                </p>
                <button class="delete" aria-label="close" on:click={()=> visible=false }></button>
            </header>
            <section class="modal-card-body">
                <slot name="body">
                    Empty
                </slot>
            </section>
            <!--<footer class="modal-card-foot">
                <button class="button is-success">Save changes</button>
                <button class="button">Cancel</button>
            </footer>-->
        </div>
    </div>
{/if}
<style type="text/scss">
</style>
