ko.bindingHandlers.inputmask =
{
    init: function (element, valueAccessor, allBindingsAccessor) {
        var mask = valueAccessor();
        var observable = mask.value;
        if (ko.isObservable(observable)) {
            $(element).on('focusout change', function () {

            	observable($(element).val());
                //Hilpold: not using below default binding but instead always using value on focus out
            	//Default binding clears value if incomplete, however our 
            	//validation is mask independent.
            	//
            	//if ($(element).inputmask('isComplete')) {
                //    observable($(element).val());
                //} else {
                //    observable(null);
                //}

            });
        }
        $(element).inputmask(mask);
    },
    update: function (element, valueAccessor, allBindings, viewModel, bindingContext) {
        var mask = valueAccessor();
        var observable = mask.value;
        if (ko.isObservable(observable)) {
            var valuetoWrite = observable();
            $(element).val(valuetoWrite);
        }
    }
};