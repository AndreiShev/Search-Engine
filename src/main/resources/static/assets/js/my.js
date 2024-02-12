window.onload = function() {
    var select = document.getElementById("locales");

    select.addEventListener("mouseover", pointerOver);
    select.addEventListener("mouseout", pointerOut);
}

function pointerOver(event){
    event.target.addEventListener("click", changeLocale);
};

function pointerOut(event){
    event.target.removeEventListener("click", changeLocale);
}

function changeLocale(event) {
    var elementVal = event.target.value;

    if (elementVal != "") {
        window.location.replace("?lang=" + elementVal);
    }
}
/*
function setEventListeners() {
  document.body.addEventListener('pointerover', this.pointerOver);
  document.body.addEventListener('pointermove', this.pointerMove);
  document.body.addEventListener('pointerout', this.pointerOut);
}

function removeEventListeners() {
  document.body.removeEventListener('pointerover', this.pointerOver);
  document.body.removeEventListener('pointermove', this.pointerMove);
  document.body.removeEventListener('pointerout', this.pointerOut);
}*/