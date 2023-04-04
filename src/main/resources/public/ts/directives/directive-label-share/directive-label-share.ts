import {ng} from "entcore";
import {RootsConst} from "../../core/constants/roots.const";
import {ILocationService, IScope, IWindowService} from "angular";


interface ILabelShareProps{
    name: string;
    isChecked: boolean;
    onSort? : () => void;
}

interface ILabelShareScope extends IScope, ILabelShareProps{
    vm : IViewModel;
}

interface IViewModel extends ng.IController, ILabelShareProps{

    sort? : () => void;
}

class Controller implements IViewModel{
    //const vm: IViewModel = <IViewModel>this;
    public name: string;
    public isChecked:boolean;
    constructor(private $scope: ILabelShareScope,
                private $location:ILocationService,
                private $window: IWindowService
                /*  inject service etc..just as we do in controller */)
    {}

    $onInit() {}

    $onDestroy() {}
}

function directive(){
    return{
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
        controller: ['$scope','$location','$window',Controller],
        link: function (scope: ILabelShareScope,
                        element: ng.IAugmentedJQuery,
                        attrs: ng.IAttributes,
                        vm: IViewModel){
            vm.sort=(): void =>{
                vm.isChecked = !vm.isChecked;
                scope.$eval(vm.onSort)(vm.isChecked);
            }

        }
    }
}
export const directiveLabelShare = ng.directive('directiveLabelShare',directive)