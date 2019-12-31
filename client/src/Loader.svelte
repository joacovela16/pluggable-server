<script>
</script>
<div class="loader-modal">
    <div class="lds-ripple">
        <div></div>
        <div></div>
    </div>
</div>
<style type="text/scss">

    .loader-modal {
        position: absolute;
        top: 0;
        left: 0;

        width: 100%;
        height: 100%;
        z-index: 100;

        background: rgba(0, 0, 0, 0.3);


        .lds-ripple {

            display: inline-block;
            position: absolute;
            width: 200px;
            height: 200px;
            left: 50%;
            top: 50%;
            margin-left: -100px;
            margin-top: -100px;
        }

        .lds-ripple div {
            position: absolute;
            border: 4px solid #fff;
            opacity: 1;
            border-radius: 50%;
            background: white;
            animation: lds-ripple 1s cubic-bezier(0, 0.2, 0.8, 1) infinite;
        }

        .lds-ripple div:nth-child(2) {
            animation-delay: -0.5s;
        }

        @keyframes lds-ripple {
            0% {
                top: 100px;
                left: 100px;
                width: 0;
                height: 0;
                opacity: 1;
            }
            100% {
                top: 0;
                left: 0;
                width: 200px;
                height: 200px;
                opacity: 0;
            }
        }
    }
</style>
