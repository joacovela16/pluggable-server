<script>
    import Axios from "axios";
    import Loader from "./Loader.svelte";

    /** @type {Promise<PluginProxy[]>} **/
    let data;

    function reloadData() {
        data = Axios.get("http://localhost:8080/state").then(response => response.data.plugins || []);
    }

    function updatePlugin(plug, action) {
        Axios.get(`http://localhost:8080/plugin/${plug.id}/${action}`).then(() => reloadData());
    }

    function reloadService(plug, service) {
        Axios.get(`http://localhost:8080/plugin/${plug.id}/${service}/reload`).then(() => reloadData());
    }

    function reloadPlugin(plug) {
        Axios
                .get(`http://localhost:8080/plugin/${plug.id}/uninstall`)
                .then(() => Axios.get(`http://localhost:8080/plugin/${plug.id}/install`))
                .then(() => reloadData())
    }

    function getDate(x) {
        let date = x.installedDate;
        return `${date.hour}:${date.minute} ${date.day}/${date.month}/${date.year}`;
    }

    /**
     * @param str {string}
     * @returns {string}
     */
    function capitalize(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    function hideCard(e){
        let children = e.currentTarget.parentNode.children;
        children[1].classList.toggle("is-hidden");
    }

    reloadData()
</script>
<section class="hero has-background-black">
    <div class="hero-body">
        <div class="container">
            <h1 class="title has-text-white-bis"> Pluggable Server </h1>
        </div>
    </div>
</section>
<section class="section">
    {#if data}
        {#await data}
            <Loader/>
        {:then xs}
            <div class="columns is-multiline">
                {#each xs as item, index (item.id)}
                    <div class="column is-offset-3 is-6">
                        {#if item.installed}
                            <div class="card has-pointer">
                                <header class="card-header" on:click={hideCard}>
                                    <p class="card-header-title">{capitalize(item.id)}</p>
                                </header>
                                <div class="card-content is-hidden">
                                    <div class="content">
                                        <div class="field is-grouped is-grouped-multiline">
                                            <div class="control">
                                                <div class="tags has-addons">
                                                    <span class="tag is-dark">State</span>
                                                    <span class="tag {item.active ? 'is-success': 'is-danger' }">{(item.active && "Enable") || "Disable" }</span>
                                                </div>
                                            </div>
                                            <div class="control">
                                                <div class="tags has-addons">
                                                    <span class="tag is-dark">Installed</span>
                                                    <span class="tag is-link">{getDate(item)}</span>
                                                </div>
                                            </div>
                                        </div>
                                        <table class="table is-hoverable is-striped">
                                            <thead>
                                            <tr>
                                                <th colSpan="1">Services</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {#each item.registry as y, index (item.id+y)}
                                                <tr>
                                                    <td>
                                                    <span title="Reload" class="button is-link is-small" style={{cursor: 'pointer'}} onClick={() => reloadService(item, y)}>
                                                        <span class="icon is-small"><i class="fas fa-sync"></i></span>
                                                    </span>
                                                        <span style={{marginLeft: "1rem"}}>{y}</span>
                                                    </td>
                                                </tr>
                                            {/each}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                                <footer class="card-footer">
                                    <a href="#" class="card-footer-item" on:click={()=>updatePlugin(item, (item.active && "disable") || "enable")}>Set as {(item.active && "disable") || "enable"}</a>
                                    {#if item.active}
                                        <a href="#" class="card-footer-item" on:click={()=> reloadPlugin(item)}>Reload</a>
                                    {/if}
                                    <a href="#" class="card-footer-item" on:click={() => updatePlugin(item, "uninstall")}>Uninstall</a>
                                </footer>
                            </div>
                        {:else}
                            <article class="message is-info">
                                <div class="message-body">
                                    Service <b>{item.id}</b> uninstalled. <a href="#" on:click={()=>updatePlugin(item, "install")}>Press to active it.</a>
                                </div>
                            </article>
                        {/if}
                    </div>
                {/each}
            </div>
        {:catch e}
        {/await}
    {/if}
</section>
<style type="text/scss">
</style>
