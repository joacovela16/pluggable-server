import "./App.scss";
import App from "./App.svelte";

document.body.innerHTML="";

const app = new App({
    target: document.body,
    props: {
    },
});

export default app;