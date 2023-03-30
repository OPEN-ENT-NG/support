import {ng} from "entcore";


interface IViewModel {
    $onInit();

    $onDestroy();

    onSort(): void;


    sort(): void;

    name: string;
    isChecked: boolean;
}

export const directiveLabelShare = ng.directive('directiveLabelShare', () => {
    return {
        templateUrl: `/support/public/ts/directives/directive-label-share/directive-label-share.html`,
        scope: {
            onSort: '&',
            name: '=',
            isChecked: '='
        },
        restrict: 'E',
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: function () {
            const vm: IViewModel = <IViewModel>this;
            vm.$onInit = async () => {
                vm.isChecked = true;
            };

            vm.$onDestroy = async () => {
                vm.isChecked = true;
            };
        },
        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.sort = (): void => {
                vm.isChecked = !vm.isChecked;
                $scope.$eval(vm.onSort)(vm.isChecked);
            }


        }
    }
})