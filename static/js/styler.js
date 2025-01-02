function addStyleDefault()
{
    document.addEventListener('DOMContentLoaded', function () {
        var formElements = document.querySelectorAll('input, select, textarea');
        formElements.forEach(function(element) {
            element.classList.add('form-control');
        });
    });
}
