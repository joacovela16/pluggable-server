import "@fortawesome/fontawesome-free/css/all.min.css";
import "./App.scss";
import "bulma/bulma.sass";

import App from "./App.svelte";

document.body.innerHTML="";

const app = new App({
    target: document.body,
    props: {
    },
});

export default app;