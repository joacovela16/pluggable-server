import "./App.scss";
import "@fortawesome/fontawesome-free/js/all.min";
import App from "./App.svelte";

document.body.innerHTML="";

const app = new App({
    target: document.body,
    props: {
    },
});

export default app;