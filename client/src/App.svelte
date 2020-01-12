<script>
    import Axios from "axios";
    import Loader from "./Loader.svelte";
    import Modal from "./Modal.svelte";

    const HOST = "http://localhost:8080";

    /** @type {Promise<PluginProxy[]>} **/
    let data;
    let serviceModalVisible = false;
    let pluginSelected;
    let file;

    function reloadData() {
        data = Axios.get(`${HOST}/state`)
                .then(response => {

                    const newVar = response.data.plugins || [];

                    if (pluginSelected) {
                        pluginSelected = newVar.find(x => x.id === pluginSelected.id)
                    }
                    return newVar;
                });
    }

    function updatePlugin(plug, action) {
        Axios.get(`${HOST}/config/plugin/${plug.id}/${action}`).then(() => reloadData());
    }

    function reloadService(plug, service) {
        Axios.get(`${HOST}/config/plugin/${plug.id}/${service}/reload`).then(() => reloadData());
    }

    function reloadPlugin(plug) {
        Axios
                .get(`${HOST}/config/plugin/${plug.id}/uninstall`)
                .then(() => Axios.get(`${HOST}/config/plugin/${plug.id}/install`))
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

    function hideCard(e) {
        let children = e.currentTarget.parentNode.children;
        children[1].classList.toggle("is-hidden");
    }

    function changeServiceState(pluginId, serviceId, action) {
        Axios.get(`${HOST}/config/service/${pluginId}/${serviceId}/${action}`).then(() => reloadData())
    }

    function targetFile(event) {
        file = event.target.files[0];
    }

    function sendModule() {
        let formData = new FormData();
        formData.append("file", file);

        Axios
                .post(`${HOST}/upload`, formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })
                .then(() => {

                    file = undefined;
                    setTimeout(() => reloadData(), 1000)
                });
    }

    reloadData()
</script>
<nav class="navbar is-black">
    <div class="navbar-brand">
        <div class="navbar-item">
            <b class="has-text-white-bis">Pluggable Server</b>
        </div>
    </div>
    <div class="navbar-menu">
        <div class="navbar-end">
            <div class="navbar-item">
                <div class="field has-addons">
                    <div class="control">
                        <input class="input is-small" type="file" name="resume" on:change={(e)=> targetFile(e)}>
                    </div>
                    {#if file}
                        <div class="control">
                            <div class="button is-small is-primary" on:click={()=>sendModule()}>Send</div>
                        </div>
                    {/if}
                </div>
            </div>
            <div class="navbar-item">
                <button class="button is-small" title="Refresh">
                    <span class="icon"><i class="fas fa-sync-alt"></i></span>
                    <span>Reload</span>
                </button>
            </div>
        </div>
    </div>
</nav>
{#if pluginSelected}
    <Modal bind:visible={serviceModalVisible}>
        <span slot="title">{pluginSelected.id}</span>
        <div class="column" slot="body">
            <table class="table is-hoverable is-fullwidth is-striped">
                <thead>
                <tr>
                    <th style="width: 1%;">Nr</th>
                    <th>Services</th>
                    <th>State</th>
                    <th style="width: 1%;"></th>
                </tr>
                </thead>
                <tbody>
                {#each pluginSelected.services as item, index (item)}
                    <tr>
                        <td><b>{index+1}</b></td>
                        <td>{item.id}</td>
                        <td>State</td>
                        <td style="width: 1%; white-space: nowrap;">
                            <div class="field has-addons">
                                {#if item.active === true}
                                    <div class="control">
                                        <button class="button is-small" title="Suspend service" on:click={()=>changeServiceState(pluginSelected.id, item.id, 'disable')}>
                                            <span class="icon is-large"><i class="fa fa-toggle-on"></i></span>
                                        </button>
                                    </div>
                                {:else}
                                    <div class="control">
                                        <button class="button is-small" title="Active service" on:click={()=>changeServiceState(pluginSelected.id, item.id, 'enable')}>
                                            <span class="icon is-large"><i class="fa fa-toggle-off"></i></span>
                                        </button>
                                    </div>
                                {/if}
                            </div>
                        </td>
                    </tr>
                {/each}
                </tbody>
            </table>
        </div>
    </Modal>
{/if}
<section class="section">
    {#if data}
        {#await data}
            <Loader/>
        {:then xs}
            <div class="columns is-multiline">
                <div class="column is-offset-2 is-8">
                    <table class="table is-hoverable is-fullwidth is-striped">
                        <thead>
                        <tr>
                            <th style="width: 5px;">Nr</th>
                            <th>Module</th>
                            <th>State</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        {#each xs as item, index (item.id)}
                            <tr class="is-vertical-align">
                                <td class="has-text-right has-pointer">
                                    <b>{index+1}</b>
                                </td>
                                <td class="has-pointer">
                                    <b>{item.id}</b>
                                </td>
                                <td class="has-pointer">
                                    {#if item.installed}
                                        <p>{(item.active && "Enable") || "Disable" }</p>
                                    {:else}
                                        <p>Uninstalled</p>
                                    {/if}
                                </td>
                                <td style="width: 1%; white-space: nowrap;">
                                    <div class="field has-addons">
                                        {#if item.installed}
                                            <div class="control">
                                                <button class="button is-small" title="Show details" on:click="{()=> {pluginSelected = item; serviceModalVisible=true;}}">
                                                    <span class="icon "><i class="fas fa-folder-open"></i></span>
                                                </button>
                                            </div>
                                            {#if item.active}
                                                <div class="control">
                                                    <button class="button is-small" title="Reload" onClick={() => reloadPlugin(item)}>
                                                        <span class="icon "><i class="fas fa-sync-alt"></i></span>
                                                    </button>
                                                </div>
                                                <div class="control">
                                                    <button class="button is-small is-warning" title="Suspend" on:click={()=>updatePlugin(item, "disable")}>
                                                        <span class="icon"><i class="fas fa-angle-double-down"></i></span>
                                                    </button>
                                                </div>
                                            {:else}
                                                <div class="control">
                                                    <button class="button is-small is-success" title="Enable" on:click={()=>updatePlugin(item,  "enable")}>
                                                        <span class="icon"><i class="fas fa-angle-double-up"></i></span>
                                                    </button>
                                                </div>
                                            {/if}

                                            <div class="control">
                                                <button class="button is-small is-danger" title="Uninstall" on:click={() => updatePlugin(item, "uninstall")}>
                                                    <span class="icon"><i class="fas fa-trash-alt"></i></span>
                                                </button>
                                            </div>
                                        {:else}
                                            <div class="control">
                                                <button class="button is-small is-primary" title="Install" on:click={() => updatePlugin(item, "install")}>
                                                    <span class="icon"><i class="fas fa-upload"></i></span>
                                                </button>
                                            </div>
                                        {/if}
                                    </div>
                                </td>
                            </tr>
                        {/each}
                        </tbody>
                    </table>
                </div>
            </div>
        {:catch e}
        {/await}
    {/if}
</section>
<style type="text/scss">
</style>
