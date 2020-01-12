<script>
    import Axios from "axios";
    import Loader from "./Loader.svelte";
    import Modal from "./Modal.svelte";
    import {onMount} from "svelte";

    const HOST = "http://localhost:8080";

    /** @type {Promise<PluginProxy[]>} **/
    let data;
    let pluginSelected;
    let files;

    let dropElem;

    let serviceModalVisible = false;
    let uploadModalVisible = false;

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

    function sendModule() {

        let promises = files.map(x => {
            let formData = new FormData();
            formData.append("file", x);
            return Axios.post(`${HOST}/upload`, formData, {headers: {'Content-Type': 'multipart/form-data'}});
        });

        Promise
                .all(promises)
                .then(() => {
                    files = [];
                    uploadModalVisible = false;
                    setTimeout(() => reloadData(), 1500)
                })
                .catch(e => console.log(e))
    }

    function onDrop(event) {
        event.preventDefault();
        files = Array.from(event.dataTransfer.files);
    }

    reloadData()
</script>
<div class="section">
    <nav class="level">
        <div class="level-left">
            <div class="level-item has-text-centered">
                <p class="title is-5">Pluggable Server</p>
            </div>
        </div>
        <div class="level-right">
            <div class="level-item">
                <button class="button is-small" title="Update modules" on:click={()=>uploadModalVisible=true}>
                    <span class="icon"><i class="fas fa-upload"></i></span>
                    <span>Upload</span>
                </button>
            </div>
            <div class="level-item">
                <button class="button is-small" title="Reload modules" on:click={()=>reloadData()}>
                    <span class="icon"><i class="fas fa-sync-alt"></i></span>
                    <span>Reload</span>
                </button>
            </div>
        </div>
    </nav>
</div>
<Modal bind:visible={uploadModalVisible}>
    <span slot="title">Upload modules</span>
    <div slot="body">
        <div class="tags">
            {#each (files||[]) as item}
                <div class="tag is-link">{item.name}</div>
            {/each}
        </div>
        <div
                bind:this={dropElem}
                on:drop|preventDefault|stopPropagation="{e=>onDrop(e)}"
                on:dragover|preventDefault|stopPropagation="{()=>{}}"
                on:dragenter|preventDefault|stopPropagation="{()=>{}}"
                class="level drop-zone"
        >
            <span class="level-item subtitle">Drop zipped files...</span>
        </div>
        <div class="level">
            <div class="level-left">
                <div class="level-item">
                    {#if files && files.length > 0}
                        <button class="button is-link" on:click={()=>sendModule()}>Send</button>
                    {/if}
                </div>
            </div>
        </div>
    </div>
</Modal>

{#if pluginSelected}
    <Modal bind:visible={serviceModalVisible}>
        <span slot="title">{pluginSelected.id}</span>
        <div class="column" slot="body">
            <table class="table is-hoverable is-fullwidth is-striped">
                <thead>
                <tr>
                    <th style="width: 1%;"></th>
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
                            <th style="width: 5px;"></th>
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
                                <td class="has-pointer">{item.id}</td>
                                <td class="has-pointer">
                                    {#if item.installed}
                                        <p>{(item.active && "Enable") || "Disable" }</p>
                                    {:else}
                                        <p>Unplugged</p>
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
                                            <div class="control">
                                                <button class="button is-small is-danger" title="Delete from file system" on:click={() => updatePlugin(item, "destroy")}>
                                                    <span class="icon"><i class="fas fa-fire-alt"></i></span>
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
    .drop-zone {
        background: rgba(0, 0, 0, 0.1);
        min-height: 20rem;
        border: 2px dashed rgba(0, 0, 0, 0.5);
    }
</style>
