"use client";

import React from "react";
import { DataTable } from '@/shared/components/ui/data-display/DataTable';
import { ComponentRendererProps } from '../types';

export const DatatableRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Table':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Name', field: 'name', flex: 1 },
            { headerName: 'Email', field: 'email', flex: 1 },
            { headerName: 'Status', field: 'status', width: 120 }
          ]}
          rows={[
            { id: 1, name: 'John Doe', email: 'john@example.com', status: 'Active' },
            { id: 2, name: 'Jane Smith', email: 'jane@example.com', status: 'Inactive' },
            { id: 3, name: 'Bob Johnson', email: 'bob@example.com', status: 'Active' },
            { id: 4, name: 'Alice Brown', email: 'alice@example.com', status: 'Pending' }
          ]}
          height={300}
        />
      );
    
    case 'With Selection':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Name', field: 'name', flex: 1 },
            { headerName: 'Email', field: 'email', flex: 1 },
            { headerName: 'Status', field: 'status', width: 120 }
          ]}
          rows={[
            { id: 1, name: 'John Doe', email: 'john@example.com', status: 'Active' },
            { id: 2, name: 'Jane Smith', email: 'jane@example.com', status: 'Inactive' },
            { id: 3, name: 'Bob Johnson', email: 'bob@example.com', status: 'Active' },
            { id: 4, name: 'Alice Brown', email: 'alice@example.com', status: 'Pending' },
            { id: 5, name: 'Charlie Wilson', email: 'charlie@example.com', status: 'Active' }
          ]}
          selectable={true}
          selectionMode="multiple"
          height={300}
        />
      );
    
    case 'With Sorting':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Name', field: 'name', flex: 1 },
            { headerName: 'Age', field: 'age', width: 100 },
            { headerName: 'Department', field: 'department', width: 150 }
          ]}
          rows={[
            { id: 1, name: 'Alice Johnson', age: 28, department: 'Engineering' },
            { id: 2, name: 'Bob Smith', age: 35, department: 'Marketing' },
            { id: 3, name: 'Charlie Brown', age: 42, department: 'Sales' },
            { id: 4, name: 'Diana Wilson', age: 31, department: 'Engineering' },
            { id: 5, name: 'Eve Davis', age: 29, department: 'HR' }
          ]}
          sort="name:asc"
          height={300}
        />
      );
    
    case 'With Filtering':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Product', field: 'product', flex: 1 },
            { headerName: 'Category', field: 'category', width: 120 },
            { headerName: 'Price', field: 'price', width: 100 },
            { headerName: 'Stock', field: 'stock', width: 100 }
          ]}
          rows={[
            { id: 1, product: 'Laptop Pro', category: 'Electronics', price: 1299, stock: 15 },
            { id: 2, product: 'Office Chair', category: 'Furniture', price: 299, stock: 8 },
            { id: 3, product: 'Wireless Mouse', category: 'Electronics', price: 49, stock: 25 },
            { id: 4, product: 'Desk Lamp', category: 'Furniture', price: 89, stock: 12 },
            { id: 5, product: 'Keyboard', category: 'Electronics', price: 79, stock: 20 }
          ]}
          height={300}
        />
      );
    
    case 'With Pagination':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Customer', field: 'customer', flex: 1 },
            { headerName: 'Order Date', field: 'orderDate', width: 120 },
            { headerName: 'Amount', field: 'amount', width: 100 }
          ]}
          rows={[
            { id: 1, customer: 'John Smith', orderDate: '2024-01-15', amount: 299.99 },
            { id: 2, customer: 'Sarah Johnson', orderDate: '2024-01-16', amount: 149.50 },
            { id: 3, customer: 'Mike Wilson', orderDate: '2024-01-17', amount: 89.99 },
            { id: 4, customer: 'Lisa Brown', orderDate: '2024-01-18', amount: 199.00 },
            { id: 5, customer: 'David Lee', orderDate: '2024-01-19', amount: 75.25 }
          ]}
          totalRows={50}
          page={0}
          pageSize={5}
          height={300}
        />
      );
    
    case 'With Toolbar':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Employee', field: 'employee', flex: 1 },
            { headerName: 'Position', field: 'position', width: 150 },
            { headerName: 'Salary', field: 'salary', width: 120 },
            { headerName: 'Department', field: 'department', width: 120 }
          ]}
          rows={[
            { id: 1, employee: 'Alice Johnson', position: 'Senior Developer', salary: 95000, department: 'Engineering' },
            { id: 2, employee: 'Bob Smith', position: 'Marketing Manager', salary: 75000, department: 'Marketing' },
            { id: 3, employee: 'Charlie Brown', position: 'Sales Director', salary: 85000, department: 'Sales' },
            { id: 4, employee: 'Diana Wilson', position: 'UX Designer', salary: 70000, department: 'Design' },
            { id: 5, employee: 'Eve Davis', position: 'HR Specialist', salary: 60000, department: 'HR' }
          ]}
          showToolbar={true}
          enableDensity={true}
          enableExport={true}
          enableColumnSelector={true}
          height={300}
        />
      );
    
    case 'Compact Density':
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 80 },
            { headerName: 'Code', field: 'code', width: 100 },
            { headerName: 'Name', field: 'name', flex: 1 },
            { headerName: 'Price', field: 'price', width: 80 },
            { headerName: 'Qty', field: 'quantity', width: 60 }
          ]}
          rows={[
            { id: 1, code: 'A001', name: 'Widget A', price: 12.99, quantity: 100 },
            { id: 2, code: 'B002', name: 'Widget B', price: 8.50, quantity: 250 },
            { id: 3, code: 'C003', name: 'Widget C', price: 15.75, quantity: 75 },
            { id: 4, code: 'D004', name: 'Widget D', price: 22.00, quantity: 50 },
            { id: 5, code: 'E005', name: 'Widget E', price: 6.25, quantity: 300 },
            { id: 6, code: 'F006', name: 'Widget F', price: 18.50, quantity: 120 }
          ]}
          initialDensity="compact"
          height={300}
        />
      );
    
    default:
      return (
        <DataTable
          columns={[
            { headerName: 'ID', field: 'id', width: 100 },
            { headerName: 'Name', field: 'name', flex: 1 },
            { headerName: 'Email', field: 'email', flex: 1 },
            { headerName: 'Status', field: 'status', width: 120 }
          ]}
          rows={[
            { id: 1, name: 'John Doe', email: 'john@example.com', status: 'Active' },
            { id: 2, name: 'Jane Smith', email: 'jane@example.com', status: 'Inactive' }
          ]}
          height={300}
        />
      );
  }
};
