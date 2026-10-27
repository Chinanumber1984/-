import { baseRequest } from '@/utils/request'
export const supplierPage = (params) => baseRequest('/srm/supplier/page', params, 'get')
