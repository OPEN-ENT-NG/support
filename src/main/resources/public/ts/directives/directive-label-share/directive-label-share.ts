import {ng} from "entcore";
import {RootsConst} from "../../core/constants/roots.const";
import {ILocationService, IScope, isFunction, IWindowService} from "angular";

interface ILabelShareProperties {
    name: string;
    isChecked: boolean;
    onSort?: () => void;
}

interface ILabelShareScope extends IScope {
    vm: ILabelShareProperties;
}

interface IViewModel extends ng.IController {

    sort?(): void;
}

class Controller implements IViewModel {
    public name: string;
    public isChecked: boolean;

    constructor(private $scope: ILabelShareScope,
                private $location: ILocationService,
                private $window: IWindowService
                /*  inject service etc..just as we do in controller */) {
    }

    $onInit() {
    }

    $onDestroy() {
    }

    sort = () => {
        if (isFunction(this.$scope.vm.onSort)) {
            this.$scope.vm.isChecked = !this.isChecked;
            this.$scope.vm.onSort();
        }
    }
}

function directive() {
    return {
        restrict: 'E',
        templateUrl: `${RootsConst.directive}directive-label-share/directive-label-share.html`,
        scope: {
            onSort: '&',
            name: '=',
            isChecked: '='
        },
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: ['$scope', '$location', '$window', Controller],
        link: function (scope: ILabelShareScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel) {
        }
    }
}

export const directiveLabelShare = ng.directive('directiveLabelShare', directive)